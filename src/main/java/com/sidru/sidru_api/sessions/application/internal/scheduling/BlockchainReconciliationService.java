package com.sidru.sidru_api.sessions.application.internal.scheduling;

import com.sidru.sidru_api.sessions.application.internal.outboundservices.blockchain.BlockchainPort;
import com.sidru.sidru_api.sessions.domain.model.aggregates.RecyclingSession;
import com.sidru.sidru_api.sessions.domain.model.valueobjects.SessionStatus;
import com.sidru.sidru_api.sessions.infrastructure.persistence.jpa.repositories.RecyclingSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Reconciles recycling sessions that were confirmed off-chain but never mirrored
 * on-chain (RF-18 resilience). When a mint fails — e.g. the backend wallet ran out of
 * POL — the session is still {@code CONFIRMED} and the citizen keeps their points, but
 * no CTC are minted and no {@code blockchainTxHash} is attached, leaving a points↔CTC
 * gap. This scheduled job periodically retries {@link BlockchainPort#recordSession} for
 * such sessions.
 *
 * <p>Safe to retry: idempotency is guaranteed by the on-chain anti-double-spend
 * ({@code sessionRecorded[sessionId]}) plus {@code findBySessionId}. If a tx already
 * exists on-chain but the hash was never persisted locally, {@code recordSession}
 * returns the existing hash without re-minting, and this job attaches it (self-healing).
 *
 * <p>Lives in the {@code sessions} context, which already depends on
 * {@code BlockchainPort}; this avoids a {@code blockchain → sessions} dependency cycle.
 */
@Service
public class BlockchainReconciliationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BlockchainReconciliationService.class);

    private final RecyclingSessionRepository sessionRepository;
    private final BlockchainPort blockchainPort;
    private final boolean blockchainEnabled;

    public BlockchainReconciliationService(RecyclingSessionRepository sessionRepository,
                                           BlockchainPort blockchainPort,
                                           @Value("${sidru.blockchain.enabled:false}") boolean blockchainEnabled) {
        this.sessionRepository = sessionRepository;
        this.blockchainPort = blockchainPort;
        this.blockchainEnabled = blockchainEnabled;
    }

    /**
     * Periodic reconciliation tick. Disabled as a whole when blockchain is off (no point
     * querying or minting). Each session is processed independently so one failure does
     * not block the rest; {@code recordSession} manages its own transaction and retries.
     */
    @Scheduled(
            initialDelayString = "${sidru.blockchain.reconciliation.initial-delay-ms:60000}",
            fixedDelayString = "${sidru.blockchain.reconciliation.interval-ms:300000}")
    public void reconcilePendingMints() {
        if (!blockchainEnabled) {
            return;
        }
        List<RecyclingSession> pending = sessionRepository
                .findTop50ByStatusAndBlockchainTxHashIsNullOrderByIdAsc(SessionStatus.CONFIRMED);
        if (pending.isEmpty()) {
            return;
        }

        LOGGER.info("Reconciliation: {} confirmed session(s) without on-chain tx — retrying mint", pending.size());
        int recovered = 0;
        for (RecyclingSession session : pending) {
            if (reconcile(session)) {
                recovered++;
            }
        }
        LOGGER.info("Reconciliation finished: {}/{} session(s) minted this run", recovered, pending.size());
    }

    /**
     * Retries the on-chain mint for a single session. Returns {@code true} if a tx hash
     * was obtained and persisted. Never throws: a failure is logged and left for the next
     * tick. The hash is saved in its own transaction; {@code recordSession} already
     * persisted the {@code BlockchainTransaction}, so a crash in between self-heals.
     */
    private boolean reconcile(RecyclingSession session) {
        try {
            return blockchainPort.recordSession(session)
                    .map(txHash -> {
                        session.attachBlockchainTx(txHash);
                        sessionRepository.save(session);
                        LOGGER.info("Reconciled session {} -> txHash {}", session.getId(), txHash);
                        return true;
                    })
                    .orElse(false);
        } catch (Exception ex) {
            LOGGER.warn("Reconciliation of session {} failed, will retry next tick: {}",
                    session.getId(), ex.getMessage());
            return false;
        }
    }
}

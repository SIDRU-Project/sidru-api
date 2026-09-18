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
 * Retries mints for sessions confirmed off-chain but never mirrored on-chain (RF-18).
 * A failed mint (e.g. backend wallet out of POL) leaves the session CONFIRMED with the
 * points credited but no CTC minted and no blockchainTxHash — a points/CTC gap this job
 * closes.
 *
 * Safe to retry: the on-chain anti-double-spend (sessionRecorded) plus findBySessionId
 * make recordSession idempotent — if the tx already exists it returns the hash without
 * re-minting and we just attach it. Lives in sessions (not blockchain) to avoid a cycle.
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
     * Periodic tick. No-op when blockchain is off. Each session is handled independently
     * so one failure does not block the rest; recordSession owns its tx and retries.
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
     * Retries the mint for one session; returns true if a hash was obtained and saved.
     * Never throws — a failure is logged and retried next tick. recordSession already
     * persisted the BlockchainTransaction, so a crash before the save self-heals.
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

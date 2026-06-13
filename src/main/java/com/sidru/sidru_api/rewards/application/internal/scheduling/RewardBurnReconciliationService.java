package com.sidru.sidru_api.rewards.application.internal.scheduling;

import com.sidru.sidru_api.rewards.application.internal.outboundservices.acl.ExternalBlockchainService;
import com.sidru.sidru_api.rewards.domain.model.aggregates.PointTransaction;
import com.sidru.sidru_api.rewards.domain.model.valueobjects.TransactionType;
import com.sidru.sidru_api.rewards.infrastructure.persistence.jpa.repositories.PointTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Reconciles reward redemptions whose CTC burn was not mirrored on-chain (RF-17 resilience).
 *
 * <p>When the burn fails at redemption time — e.g. the custodial address had insufficient
 * CTC because mints were still pending, or an RPC error — the off-chain redemption still
 * completes (points deducted) by design, but no {@code blockchainTxHash} is attached. This
 * scheduled job periodically retries the burn for such {@code REDEEM} transactions.
 *
 * <p>Safe to retry: the on-chain {@code redeemFrom} is idempotent per {@code rewardTxId}
 * (anti double-redeem). If a burn already happened on-chain but its hash was never persisted,
 * {@code burnForRedemption} detects it ({@code rewardRedeemed}) and confirms without burning
 * again, so this job can never double-burn.
 *
 * <p>Lives in the {@code rewards} context, which owns {@code PointTransaction} and already
 * depends on {@code blockchain} via {@link ExternalBlockchainService}.
 */
@Service
public class RewardBurnReconciliationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RewardBurnReconciliationService.class);

    private final PointTransactionRepository transactionRepository;
    private final ExternalBlockchainService externalBlockchainService;
    private final boolean blockchainEnabled;

    public RewardBurnReconciliationService(PointTransactionRepository transactionRepository,
                                           ExternalBlockchainService externalBlockchainService,
                                           @Value("${sidru.blockchain.enabled:false}") boolean blockchainEnabled) {
        this.transactionRepository = transactionRepository;
        this.externalBlockchainService = externalBlockchainService;
        this.blockchainEnabled = blockchainEnabled;
    }

    /**
     * Periodic reconciliation tick. Disabled as a whole when blockchain is off. Each
     * redemption is processed independently; the burn manages its own transaction.
     */
    @Scheduled(
            initialDelayString = "${sidru.blockchain.reconciliation.initial-delay-ms:60000}",
            fixedDelayString = "${sidru.blockchain.reconciliation.interval-ms:300000}")
    public void reconcilePendingBurns() {
        if (!blockchainEnabled) {
            return;
        }
        List<PointTransaction> pending = transactionRepository
                .findTop50ByTypeAndBlockchainTxHashIsNullOrderByIdAsc(TransactionType.REDEEM);
        if (pending.isEmpty()) {
            return;
        }

        LOGGER.info("Burn reconciliation: {} redemption(s) without on-chain burn — retrying", pending.size());
        int recovered = 0;
        for (PointTransaction tx : pending) {
            if (reconcile(tx)) {
                recovered++;
            }
        }
        LOGGER.info("Burn reconciliation finished: {}/{} burned this run", recovered, pending.size());
    }

    /**
     * Retries the on-chain burn for a single redemption. Returns {@code true} if a tx hash
     * (or idempotent marker) was obtained and persisted. Never throws.
     */
    private boolean reconcile(PointTransaction tx) {
        try {
            return externalBlockchainService
                    .burnForRedemption(tx.getUserId(), tx.getPoints(), tx.getId())
                    .map(txHash -> {
                        tx.attachBlockchainTx(txHash);
                        transactionRepository.save(tx);
                        LOGGER.info("Reconciled burn for reward tx {} -> {}", tx.getId(), txHash);
                        return true;
                    })
                    .orElse(false);
        } catch (Exception ex) {
            LOGGER.warn("Burn reconciliation of reward tx {} failed, will retry next tick: {}",
                    tx.getId(), ex.getMessage());
            return false;
        }
    }
}

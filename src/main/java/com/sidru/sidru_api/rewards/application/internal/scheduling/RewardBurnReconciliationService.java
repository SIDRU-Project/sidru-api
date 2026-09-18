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
 * Retries reward redemptions whose CTC burn was not mirrored on-chain (RF-17).
 * A failed burn (custodial address short on CTC because mints were still pending, or an RPC
 * error) still lets the off-chain redemption complete with points deducted, but leaves no
 * blockchainTxHash — this job retries the burn for such REDEEM transactions.
 *
 * Safe to retry: on-chain redeemFrom is idempotent per rewardTxId (anti double-redeem). If
 * the burn already happened but its hash was lost, burnForRedemption detects it and confirms
 * without burning again, so it can never double-burn. Lives in rewards (owns PointTransaction,
 * already depends on blockchain via ExternalBlockchainService).
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
     * Periodic tick. No-op when blockchain is off. Each redemption is handled independently;
     * the burn owns its transaction.
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
     * Retries the burn for one redemption; returns true if a hash (or idempotent marker)
     * was obtained and saved. Never throws.
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

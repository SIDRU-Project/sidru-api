package com.sidru.sidru_api.blockchain.application.acl;

import com.sidru.sidru_api.blockchain.application.internal.custodial.CustodialWalletService;
import com.sidru.sidru_api.blockchain.infrastructure.web3j.ChapaTuCriptoContract;
import com.sidru.sidru_api.blockchain.infrastructure.web3j.config.BlockchainProperties;
import com.sidru.sidru_api.blockchain.interfaces.acl.BlockchainRedemptionFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigInteger;
import java.util.Optional;

/**
 * Implementation of the blockchain redemption ACL.
 *
 * <p>Resolves the citizen's custodial address and burns {@code pointsCost} CTC via the
 * privileged {@code redeemFrom} call (backend pays gas, custodial never signs). Honors
 * {@code BLOCKCHAIN_ENABLED} and is strictly best-effort: any failure is caught and
 * logged, never propagated, so the off-chain reward redemption is never rolled back.
 */
@Service
public class BlockchainRedemptionFacadeImpl implements BlockchainRedemptionFacade {

    private static final Logger LOGGER = LoggerFactory.getLogger(BlockchainRedemptionFacadeImpl.class);
    private static final BigInteger WEI_PER_CTC = BigInteger.TEN.pow(18);
    /** Pseudo-hash stored when the burn was already done on-chain (idempotent confirmation). */
    private static final String ALREADY_REDEEMED_MARKER = "redeemed:";

    private final CustodialWalletService custodialWalletService;
    private final ChapaTuCriptoContract contract;
    private final BlockchainProperties properties;

    public BlockchainRedemptionFacadeImpl(CustodialWalletService custodialWalletService,
                                          ChapaTuCriptoContract contract,
                                          BlockchainProperties properties) {
        this.custodialWalletService = custodialWalletService;
        this.contract = contract;
        this.properties = properties;
    }

    @Override
    public Optional<String> redeemForReward(Long userId, int pointsCost, Long rewardTxId) {
        if (!properties.isEnabled()) {
            LOGGER.debug("Blockchain disabled — skipping CTC burn for reward tx {}", rewardTxId);
            return Optional.empty();
        }
        if (userId == null || pointsCost <= 0) {
            LOGGER.debug("Skipping CTC burn: userId={} pointsCost={}", userId, pointsCost);
            return Optional.empty();
        }
        BigInteger onChainRewardTxId = BigInteger.valueOf(rewardTxId);
        try {
            // Idempotency: if this rewardTxId was already burned on-chain (e.g. a previous
            // attempt succeeded but its hash was not persisted), do NOT burn again.
            if (contract.rewardRedeemed(onChainRewardTxId)) {
                LOGGER.info("Reward tx {} already redeemed on-chain — confirming without re-burning", rewardTxId);
                return Optional.of(ALREADY_REDEEMED_MARKER + rewardTxId);
            }
            String custodial = custodialWalletService.addressFor(userId);
            BigInteger amount = BigInteger.valueOf(pointsCost).multiply(WEI_PER_CTC);
            TransactionReceipt receipt = contract.redeemFrom(custodial, amount, onChainRewardTxId);
            if (receipt == null || !receipt.isStatusOK()) {
                // Reverted: re-check idempotency (a concurrent burn may have won the race).
                if (isRedeemed(onChainRewardTxId)) {
                    return Optional.of(ALREADY_REDEEMED_MARKER + rewardTxId);
                }
                LOGGER.warn("redeemFrom reverted for reward tx {} (user {}); off-chain redemption intact "
                        + "(insufficient CTC?). Reconciliation will retry.", rewardTxId, userId);
                return Optional.empty();
            }
            LOGGER.info("Burned {} CTC for reward tx {} (user {}), txHash={}",
                    pointsCost, rewardTxId, userId, receipt.getTransactionHash());
            return Optional.of(receipt.getTransactionHash());
        } catch (Exception ex) {
            // Best-effort: e.g. insufficient custodial CTC (mint backlog) or RPC error.
            // Never break the off-chain redemption; the points were already deducted.
            LOGGER.error("CTC burn failed for reward tx {} (user {}); off-chain redemption intact: {}",
                    rewardTxId, userId, ex.getMessage());
            return Optional.empty();
        }
    }

    /** Best-effort on-chain idempotency re-check; never throws. */
    private boolean isRedeemed(BigInteger rewardTxId) {
        try {
            return contract.rewardRedeemed(rewardTxId);
        } catch (Exception ex) {
            return false;
        }
    }
}

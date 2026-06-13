package com.sidru.sidru_api.blockchain.interfaces.acl;

import java.util.Optional;

/**
 * Anti-corruption layer (ACL) of the {@code blockchain} bounded context for reward
 * redemptions.
 *
 * <p>Exposes a minimal, stable contract so the {@code rewards} context can keep the
 * on-chain CTC balance in sync with an off-chain points deduction (burn) without
 * depending on Web3j, the custodial-address derivation, or any contract detail. This
 * mirrors {@code notifications.interfaces.acl.NotificationContextFacade}: inbound
 * coupling stays at the interface boundary.
 */
public interface BlockchainRedemptionFacade {

    /**
     * Best-effort burn of CTC from a citizen's custodial address when a reward is
     * redeemed (1 point = 1 CTC = 10^18 wei).
     *
     * <p>Implementations must never propagate exceptions: the off-chain redemption
     * (points already deducted) is the source of truth and must not be rolled back if
     * the on-chain burn fails (insufficient custodial CTC, RPC error, blockchain
     * disabled). Idempotency is satisfied by construction: the caller invokes this
     * exactly once per redemption, keyed by the unique {@code rewardTxId}.
     *
     * @param userId     citizen id (resolved to their deterministic custodial address)
     * @param pointsCost off-chain points spent on the reward (== CTC to burn)
     * @param rewardTxId off-chain point-transaction id (on-chain traceability)
     * @return the burn transaction hash if it was executed on-chain, otherwise empty
     *         (blockchain disabled, zero amount, or a best-effort failure)
     */
    Optional<String> redeemForReward(Long userId, int pointsCost, Long rewardTxId);
}

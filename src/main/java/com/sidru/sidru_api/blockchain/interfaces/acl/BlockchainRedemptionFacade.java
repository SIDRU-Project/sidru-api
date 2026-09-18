package com.sidru.sidru_api.blockchain.interfaces.acl;

import java.util.Optional;

/**
 * ACL of the {@code blockchain} context for reward redemptions: lets {@code rewards} keep
 * the on-chain CTC balance in sync with the off-chain points deduction (burn) without
 * depending on Web3j or the custodial-address derivation.
 */
public interface BlockchainRedemptionFacade {

    /**
     * Best-effort burn of CTC from the citizen's custodial address on reward redemption
     * (1 point = 1 CTC = 10^18 wei). Never propagates exceptions: the off-chain redemption
     * (points already deducted) is the source of truth and is not rolled back if the burn
     * fails. Idempotent: invoked exactly once per redemption, keyed by {@code rewardTxId}.
     *
     * @param userId     citizen id (resolved to the custodial address)
     * @param pointsCost points spent on the reward (== CTC to burn)
     * @param rewardTxId off-chain point-transaction id (on-chain traceability)
     * @return burn tx hash if executed on-chain, otherwise empty
     */
    Optional<String> redeemForReward(Long userId, int pointsCost, Long rewardTxId);
}

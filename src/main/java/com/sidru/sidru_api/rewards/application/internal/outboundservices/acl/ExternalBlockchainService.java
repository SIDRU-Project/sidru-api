package com.sidru.sidru_api.rewards.application.internal.outboundservices.acl;

import com.sidru.sidru_api.blockchain.interfaces.acl.BlockchainRedemptionFacade;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Outbound ACL of rewards towards blockchain. Lets the redemption flow keep the on-chain
 * CTC balance in sync (burn) without knowing Web3j or custodial addresses — it only
 * depends on BlockchainRedemptionFacade. Same pattern as ExternalUserProfileService.
 */
@Service("rewardsExternalBlockchainService")
public class ExternalBlockchainService {

    private final BlockchainRedemptionFacade blockchainRedemptionFacade;

    public ExternalBlockchainService(BlockchainRedemptionFacade blockchainRedemptionFacade) {
        this.blockchainRedemptionFacade = blockchainRedemptionFacade;
    }

    /**
     * Best-effort CTC burn mirroring an off-chain points deduction. Never throws.
     * @return the burn tx hash if executed on-chain, otherwise empty.
     */
    public Optional<String> burnForRedemption(Long userId, int pointsCost, Long rewardTxId) {
        return blockchainRedemptionFacade.redeemForReward(userId, pointsCost, rewardTxId);
    }
}

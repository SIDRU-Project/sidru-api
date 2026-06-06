package com.sidru.sidru_api.rewards.application.internal.commandservices;

import com.sidru.sidru_api.rewards.application.internal.outboundservices.acl.ExternalBlockchainService;
import com.sidru.sidru_api.rewards.application.internal.outboundservices.acl.ExternalUserProfileService;
import com.sidru.sidru_api.rewards.domain.model.aggregates.PointTransaction;
import com.sidru.sidru_api.rewards.domain.model.aggregates.Reward;
import com.sidru.sidru_api.rewards.domain.model.commands.CreateRewardCommand;
import com.sidru.sidru_api.rewards.domain.model.commands.RedeemRewardCommand;
import com.sidru.sidru_api.rewards.domain.model.exceptions.InsufficientPointsToRedeemException;
import com.sidru.sidru_api.rewards.domain.model.exceptions.RewardNotFoundException;
import com.sidru.sidru_api.rewards.domain.model.exceptions.RewardOutOfStockException;
import com.sidru.sidru_api.rewards.domain.services.RewardCommandService;
import com.sidru.sidru_api.rewards.infrastructure.persistence.jpa.repositories.PointTransactionRepository;
import com.sidru.sidru_api.rewards.infrastructure.persistence.jpa.repositories.RewardRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class RewardCommandServiceImpl implements RewardCommandService {

    private final RewardRepository rewardRepository;
    private final PointTransactionRepository transactionRepository;
    private final ExternalUserProfileService externalUserProfileService;
    private final ExternalBlockchainService externalBlockchainService;

    public RewardCommandServiceImpl(RewardRepository rewardRepository,
                                    PointTransactionRepository transactionRepository,
                                    ExternalUserProfileService externalUserProfileService,
                                    ExternalBlockchainService externalBlockchainService) {
        this.rewardRepository = rewardRepository;
        this.transactionRepository = transactionRepository;
        this.externalUserProfileService = externalUserProfileService;
        this.externalBlockchainService = externalBlockchainService;
    }

    @Override
    @Transactional
    public Optional<Reward> handle(CreateRewardCommand command) {
        var reward = new Reward(command.name(), command.description(),
                command.pointsCost(), command.stock(), command.imageUrl());
        return Optional.of(rewardRepository.save(reward));
    }

    @Override
    @Transactional
    public Optional<PointTransaction> handle(RedeemRewardCommand command) {
        var reward = rewardRepository.findById(command.rewardId())
                .orElseThrow(RewardNotFoundException::new);

        if (!reward.isActive() || !reward.hasStock()) {
            throw new RewardOutOfStockException();
        }

        int totalPoints = externalUserProfileService.fetchTotalPoints(command.userId());
        if (totalPoints < reward.getPointsCost()) {
            throw new InsufficientPointsToRedeemException();
        }

        externalUserProfileService.subtractPoints(command.userId(), reward.getPointsCost());

        reward.decrementStock();
        rewardRepository.save(reward);

        var tx = transactionRepository.save(
                PointTransaction.redeem(command.userId(), reward.getPointsCost(),
                        reward.getId(), reward.getName()));

        // Mirror the points deduction on-chain by burning CTC (best-effort, never breaks
        // the off-chain redemption). Keyed by the unique point-transaction id. The hash is
        // flushed onto the managed entity on commit (dirty checking), no second save needed.
        externalBlockchainService
                .burnForRedemption(command.userId(), reward.getPointsCost(), tx.getId())
                .ifPresent(tx::attachBlockchainTx);

        return Optional.of(tx);
    }
}

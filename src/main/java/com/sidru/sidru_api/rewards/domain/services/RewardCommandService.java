package com.sidru.sidru_api.rewards.domain.services;

import com.sidru.sidru_api.rewards.domain.model.aggregates.PointTransaction;
import com.sidru.sidru_api.rewards.domain.model.aggregates.Reward;
import com.sidru.sidru_api.rewards.domain.model.commands.CreateRewardCommand;
import com.sidru.sidru_api.rewards.domain.model.commands.RedeemRewardCommand;

import java.util.Optional;

public interface RewardCommandService {
    Optional<Reward> handle(CreateRewardCommand command);
    Optional<PointTransaction> handle(RedeemRewardCommand command);
}

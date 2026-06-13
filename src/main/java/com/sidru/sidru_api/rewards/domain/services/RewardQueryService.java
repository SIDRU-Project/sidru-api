package com.sidru.sidru_api.rewards.domain.services;

import com.sidru.sidru_api.rewards.domain.model.aggregates.PointTransaction;
import com.sidru.sidru_api.rewards.domain.model.aggregates.Reward;
import com.sidru.sidru_api.rewards.domain.model.queries.GetAllActiveRewardsQuery;
import com.sidru.sidru_api.rewards.domain.model.queries.GetPointTransactionsByUserIdQuery;
import com.sidru.sidru_api.rewards.domain.model.queries.GetRewardByIdQuery;

import java.util.List;
import java.util.Optional;

public interface RewardQueryService {
    List<Reward> handle(GetAllActiveRewardsQuery query);
    Optional<Reward> handle(GetRewardByIdQuery query);
    List<PointTransaction> handle(GetPointTransactionsByUserIdQuery query);
}

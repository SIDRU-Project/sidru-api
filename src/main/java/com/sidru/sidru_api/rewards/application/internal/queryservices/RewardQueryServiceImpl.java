package com.sidru.sidru_api.rewards.application.internal.queryservices;

import com.sidru.sidru_api.rewards.domain.model.aggregates.PointTransaction;
import com.sidru.sidru_api.rewards.domain.model.aggregates.Reward;
import com.sidru.sidru_api.rewards.domain.model.queries.GetAllActiveRewardsQuery;
import com.sidru.sidru_api.rewards.domain.model.queries.GetPointTransactionsByUserIdQuery;
import com.sidru.sidru_api.rewards.domain.model.queries.GetRewardByIdQuery;
import com.sidru.sidru_api.rewards.domain.services.RewardQueryService;
import com.sidru.sidru_api.rewards.infrastructure.persistence.jpa.repositories.PointTransactionRepository;
import com.sidru.sidru_api.rewards.infrastructure.persistence.jpa.repositories.RewardRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class RewardQueryServiceImpl implements RewardQueryService {

    private final RewardRepository rewardRepository;
    private final PointTransactionRepository transactionRepository;

    public RewardQueryServiceImpl(RewardRepository rewardRepository,
                                  PointTransactionRepository transactionRepository) {
        this.rewardRepository = rewardRepository;
        this.transactionRepository = transactionRepository;
    }

    @Override
    public List<Reward> handle(GetAllActiveRewardsQuery query) {
        return rewardRepository.findByActiveTrueOrderByPointsCostAsc();
    }

    @Override
    public Optional<Reward> handle(GetRewardByIdQuery query) {
        return rewardRepository.findById(query.id());
    }

    @Override
    public List<PointTransaction> handle(GetPointTransactionsByUserIdQuery query) {
        return transactionRepository.findByUserIdOrderByCreatedAtDesc(query.userId());
    }
}

package com.sidru.sidru_api.rewards.infrastructure.persistence.jpa.repositories;

import com.sidru.sidru_api.rewards.domain.model.aggregates.Reward;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RewardRepository extends JpaRepository<Reward, Long> {
    List<Reward> findByActiveTrueOrderByPointsCostAsc();

    Optional<Reward> findByName(String name);
}

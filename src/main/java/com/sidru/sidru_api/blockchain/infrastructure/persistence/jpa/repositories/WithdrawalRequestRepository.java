package com.sidru.sidru_api.blockchain.infrastructure.persistence.jpa.repositories;

import com.sidru.sidru_api.blockchain.domain.model.aggregates.WithdrawalRequest;
import com.sidru.sidru_api.blockchain.domain.model.valueobjects.WithdrawalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WithdrawalRequestRepository extends JpaRepository<WithdrawalRequest, Long> {
    List<WithdrawalRequest> findByUserIdAndStatus(Long userId, WithdrawalStatus status);

    Optional<WithdrawalRequest> findTopByUserIdOrderByIdDesc(Long userId);
}

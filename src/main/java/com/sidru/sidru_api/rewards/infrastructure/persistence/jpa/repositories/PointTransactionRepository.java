package com.sidru.sidru_api.rewards.infrastructure.persistence.jpa.repositories;

import com.sidru.sidru_api.rewards.domain.model.aggregates.PointTransaction;
import com.sidru.sidru_api.rewards.domain.model.valueobjects.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PointTransactionRepository extends JpaRepository<PointTransaction, Long> {
    List<PointTransaction> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * Redemptions whose CTC burn was not mirrored on-chain yet (no tx hash). Used by the
     * burn reconciliation job to retry. Bounded per tick, oldest-first.
     */
    List<PointTransaction> findTop50ByTypeAndBlockchainTxHashIsNullOrderByIdAsc(TransactionType type);
}

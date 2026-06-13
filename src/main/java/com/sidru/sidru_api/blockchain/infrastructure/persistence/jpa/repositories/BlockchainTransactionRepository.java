package com.sidru.sidru_api.blockchain.infrastructure.persistence.jpa.repositories;

import com.sidru.sidru_api.blockchain.domain.model.aggregates.BlockchainTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BlockchainTransactionRepository extends JpaRepository<BlockchainTransaction, Long> {
    Optional<BlockchainTransaction> findBySessionId(Long sessionId);

    List<BlockchainTransaction> findByUserIdOrderByIdDesc(Long userId);
}

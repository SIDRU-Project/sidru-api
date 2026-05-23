package com.sidru.sidru_api.sessions.infrastructure.persistence.jpa.repositories;

import com.sidru.sidru_api.sessions.domain.model.aggregates.RecyclingSession;
import com.sidru.sidru_api.sessions.domain.model.valueobjects.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RecyclingSessionRepository extends JpaRepository<RecyclingSession, Long> {
    Optional<RecyclingSession> findByQrToken(String qrToken);
    List<RecyclingSession> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * Sessions confirmed off-chain but not yet mirrored on-chain (no tx hash attached).
     * Used by the reconciliation job to retry failed/missed mints. Bounded per tick and
     * ordered oldest-first so a backlog drains deterministically.
     */
    List<RecyclingSession> findTop50ByStatusAndBlockchainTxHashIsNullOrderByIdAsc(SessionStatus status);
}

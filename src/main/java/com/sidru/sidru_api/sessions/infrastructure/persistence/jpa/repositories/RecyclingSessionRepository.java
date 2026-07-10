package com.sidru.sidru_api.sessions.infrastructure.persistence.jpa.repositories;

import com.sidru.sidru_api.sessions.domain.model.aggregates.RecyclingSession;
import com.sidru.sidru_api.sessions.domain.model.valueobjects.SessionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RecyclingSessionRepository extends JpaRepository<RecyclingSession, Long> {
    Optional<RecyclingSession> findByQrToken(String qrToken);
    List<RecyclingSession> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * Anti-doble-canje (US-23): lee la sesión tomando un bloqueo de escritura
     * (SELECT ... FOR UPDATE) sobre la fila. Serializa confirmaciones concurrentes
     * del mismo QR: la 2ª solicitud espera a que la 1ª haga commit y entonces la lee
     * ya CONFIRMED, fallando el guard {@code isPending()} sin re-acreditar ni mintear.
     * Solo debe usarse dentro de la transacción de confirmación.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RecyclingSession s where s.qrToken = :qrToken")
    Optional<RecyclingSession> findByQrTokenForUpdate(@Param("qrToken") String qrToken);

    // ───────── Agregaciones para métricas de impacto (US-36) ─────────
    long countByStatus(SessionStatus status);

    @Query("select coalesce(sum(s.capCount), 0) from RecyclingSession s where s.status = :status")
    long sumCapCountByStatus(@Param("status") SessionStatus status);

    @Query("select coalesce(sum(s.weightGrams), 0.0) from RecyclingSession s where s.status = :status")
    double sumWeightGramsByStatus(@Param("status") SessionStatus status);

    @Query("select coalesce(sum(s.pointsEarned), 0) from RecyclingSession s where s.status = :status")
    long sumPointsEarnedByStatus(@Param("status") SessionStatus status);

    /**
     * Sessions confirmed off-chain but not yet mirrored on-chain (no tx hash attached).
     * Used by the reconciliation job to retry failed/missed mints. Bounded per tick and
     * ordered oldest-first so a backlog drains deterministically.
     */
    List<RecyclingSession> findTop50ByStatusAndBlockchainTxHashIsNullOrderByIdAsc(SessionStatus status);
}

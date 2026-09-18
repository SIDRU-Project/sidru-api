package com.sidru.sidru_api.sessions.infrastructure.persistence.jpa.repositories;

import com.sidru.sidru_api.sessions.domain.model.aggregates.RecyclingSession;
import com.sidru.sidru_api.sessions.domain.model.valueobjects.BinActivity;
import com.sidru.sidru_api.sessions.domain.model.valueobjects.SessionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RecyclingSessionRepository extends JpaRepository<RecyclingSession, Long> {
    Optional<RecyclingSession> findByQrToken(String qrToken);
    List<RecyclingSession> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** Historial paginado del ciudadano, del mas reciente al mas antiguo (CP016 / US-22). */
    Page<RecyclingSession> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /**
     * Anti-doble-canje (US-23): SELECT ... FOR UPDATE sobre la fila. Serializa las
     * confirmaciones concurrentes del mismo QR: la 2ª espera el commit de la 1ª y la lee
     * ya CONFIRMED, así falla el guard isPending() sin re-acreditar ni mintear. Usar solo
     * dentro de la transacción de confirmación.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RecyclingSession s where s.qrToken = :qrToken")
    Optional<RecyclingSession> findByQrTokenForUpdate(@Param("qrToken") String qrToken);

    // Agregaciones para métricas de impacto (US-36)
    long countByStatus(SessionStatus status);

    @Query("select coalesce(sum(s.capCount), 0) from RecyclingSession s where s.status = :status")
    long sumCapCountByStatus(@Param("status") SessionStatus status);

    @Query("select coalesce(sum(s.weightGrams), 0.0) from RecyclingSession s where s.status = :status")
    double sumWeightGramsByStatus(@Param("status") SessionStatus status);

    @Query("select coalesce(sum(s.pointsEarned), 0) from RecyclingSession s where s.status = :status")
    long sumPointsEarnedByStatus(@Param("status") SessionStatus status);

    // --- Agregaciones por rango de fechas para el dashboard operativo (CP044 / US-36) ---

    @Query("select count(s) from RecyclingSession s where s.createdAt between :from and :to")
    long countAllInRange(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select count(s) from RecyclingSession s "
            + "where s.status = :status and s.createdAt between :from and :to")
    long countByStatusInRange(@Param("status") SessionStatus status,
                              @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select coalesce(sum(s.capCount), 0) from RecyclingSession s "
            + "where s.status = :status and s.createdAt between :from and :to")
    long sumCapCountByStatusInRange(@Param("status") SessionStatus status,
                                    @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select coalesce(sum(s.weightGrams), 0.0) from RecyclingSession s "
            + "where s.status = :status and s.createdAt between :from and :to")
    double sumWeightGramsByStatusInRange(@Param("status") SessionStatus status,
                                         @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select coalesce(sum(s.pointsEarned), 0) from RecyclingSession s "
            + "where s.status = :status and s.createdAt between :from and :to")
    long sumPointsEarnedByStatusInRange(@Param("status") SessionStatus status,
                                        @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** Ciudadanos distintos con al menos una sesion confirmada en la ventana (usuarios activos). */
    @Query("select count(distinct s.userId) from RecyclingSession s "
            + "where s.status = :status and s.userId is not null and s.createdAt between :from and :to")
    long countDistinctUsersByStatusInRange(@Param("status") SessionStatus status,
                                           @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** Ranking de Smart Bins por sesiones confirmadas en la ventana, de mayor a menor. */
    @Query("select new com.sidru.sidru_api.sessions.domain.model.valueobjects.BinActivity("
            + "s.smartBinId, count(s), coalesce(sum(s.capCount), 0), coalesce(sum(s.weightGrams), 0.0)) "
            + "from RecyclingSession s "
            + "where s.status = :status and s.createdAt between :from and :to "
            + "group by s.smartBinId order by count(s) desc, s.smartBinId asc")
    List<BinActivity> rankBinsByStatusInRange(@Param("status") SessionStatus status,
                                              @Param("from") LocalDateTime from,
                                              @Param("to") LocalDateTime to,
                                              Pageable pageable);

    /**
     * Sessions confirmed off-chain but not yet mirrored on-chain (no tx hash yet).
     * The reconciliation job retries these; bounded per tick and oldest-first.
     */
    List<RecyclingSession> findTop50ByStatusAndBlockchainTxHashIsNullOrderByIdAsc(SessionStatus status);
}

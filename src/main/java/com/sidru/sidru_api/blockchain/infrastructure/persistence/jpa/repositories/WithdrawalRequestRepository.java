package com.sidru.sidru_api.blockchain.infrastructure.persistence.jpa.repositories;

import com.sidru.sidru_api.blockchain.domain.model.aggregates.WithdrawalRequest;
import com.sidru.sidru_api.blockchain.domain.model.valueobjects.WithdrawalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WithdrawalRequestRepository extends JpaRepository<WithdrawalRequest, Long> {
    List<WithdrawalRequest> findByUserIdAndStatus(Long userId, WithdrawalStatus status);

    Optional<WithdrawalRequest> findTopByUserIdOrderByIdDesc(Long userId);

    /** Historial completo del usuario, del mas reciente al mas antiguo (GET /wallet/me/withdrawals). */
    List<WithdrawalRequest> findAllByUserIdOrderByIdDesc(Long userId);

    /** Candidatos a reconciliar: los EN_PROCESO mas antiguos primero, tope 50 por corrida. */
    List<WithdrawalRequest> findTop50ByStatusAndUpdatedAtBeforeOrderByIdAsc(
            WithdrawalStatus status, LocalDateTime updatedAtBefore);

    /** Suma de puntos de retiros en un estado, dentro de un rango de creacion (metricas, CP044). */
    @Query("SELECT COALESCE(SUM(w.points), 0) FROM WithdrawalRequest w "
            + "WHERE w.status = :status AND w.createdAt BETWEEN :from AND :to")
    long sumPointsByStatusAndCreatedAtBetween(@Param("status") WithdrawalStatus status,
                                              @Param("from") LocalDateTime from,
                                              @Param("to") LocalDateTime to);
}

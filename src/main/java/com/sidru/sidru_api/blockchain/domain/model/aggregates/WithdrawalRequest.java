package com.sidru.sidru_api.blockchain.domain.model.aggregates;

import com.sidru.sidru_api.blockchain.domain.model.valueobjects.ChainWithdrawalIds;
import com.sidru.sidru_api.blockchain.domain.model.valueobjects.WithdrawalMode;
import com.sidru.sidru_api.blockchain.domain.model.valueobjects.WithdrawalStatus;
import com.sidru.sidru_api.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Aggregate tracking a citizen's request to withdraw app points to CTC (mint) or USDC
 * (reserve payout). Persisted status enforces idempotency (RN-BC-07): the on-chain
 * withdrawalId shared by mintWithdrawal/payoutReserve is {@link #chainWithdrawalId},
 * a random id independent of this row's own DB id (two databases against the same
 * contract must never collide).
 */
@Getter
@Setter
@Entity
@Table(name = "withdrawal_requests")
public class WithdrawalRequest extends AuditableAbstractAggregateRoot<WithdrawalRequest> {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Citizen external wallet (validated EIP-55 before persisting). */
    @Column(name = "to_address", nullable = false, length = 64)
    private String toAddress;

    /** Points debited (1 point = 1 CTC). */
    @Column(nullable = false)
    private int points;

    /** Amount in wei: points * 10^18. */
    @Column(name = "amount_wei", nullable = false, length = 100)
    private String amountWei;

    @Enumerated(EnumType.STRING)
    @Column(length = 8, nullable = false)
    private WithdrawalMode mode;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private WithdrawalStatus status;

    @Column(length = 256)
    private String txHash;

    /** Envios intentados; la reconciliacion corta en max-attempts. InsufficientReserve no cuenta. */
    @Column(nullable = false)
    private int attempts;

    @Column(name = "failure_reason", length = 256)
    private String failureReason;

    /** Momento de la devolucion de puntos (null si no aplica). */
    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    /** Solo modo USDC: reserva entregada (string, 6 decimales). Null en modo CTC. */
    @Column(name = "reserve_out", length = 100)
    private String reserveOut;

    /**
     * withdrawalId enviado a mintWithdrawal/payoutReserve. Nullable a propósito: ddl-auto=update
     * no puede añadir una columna NOT NULL con filas existentes, y todas las filas anteriores a
     * este campo ya están en estado final.
     */
    @Column(name = "chain_withdrawal_id", unique = true)
    private Long chainWithdrawalId;

    public WithdrawalRequest() {}

    public WithdrawalRequest(Long userId, String toAddress, int points, String amountWei, WithdrawalMode mode) {
        this.userId = userId;
        this.toAddress = toAddress;
        this.points = points;
        this.amountWei = amountWei;
        this.mode = mode;
        this.status = WithdrawalStatus.EN_PROCESO;
        this.attempts = 0;
        this.chainWithdrawalId = ChainWithdrawalIds.next();
    }

    /** Un envio real a la cadena (no cuenta cuando el motivo es InsufficientReserve). */
    public void markAttempt() {
        this.attempts++;
    }

    public void complete(String txHash) {
        this.txHash = txHash;
        this.status = WithdrawalStatus.COMPLETADO;
    }

    /** Confirmado por lectura on-chain (withdrawalProcessed(id) == true), no por recibo de tx. */
    public void completeFromChain(Long withdrawalId) {
        complete("recorded:" + withdrawalId);
    }

    public void fail(String reason) {
        this.failureReason = reason;
        this.status = WithdrawalStatus.FALLIDO;
    }

    public void markRefunded() {
        this.refundedAt = LocalDateTime.now();
    }
}

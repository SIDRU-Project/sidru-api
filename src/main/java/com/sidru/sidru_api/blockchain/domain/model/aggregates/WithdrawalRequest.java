package com.sidru.sidru_api.blockchain.domain.model.aggregates;

import com.sidru.sidru_api.blockchain.domain.model.valueobjects.WithdrawalStatus;
import com.sidru.sidru_api.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Aggregate tracking a citizen's request to withdraw their full custodial CTC
 * balance to an external wallet. Persisted status enforces idempotency (RN-BC-07).
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

    /** Amount in wei (full custodial balance at request time). */
    @Column(name = "amount_wei", nullable = false, length = 100)
    private String amountWei;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private WithdrawalStatus status;

    @Column(length = 256)
    private String txHash;

    public WithdrawalRequest() {}

    public WithdrawalRequest(Long userId, String toAddress, String amountWei) {
        this.userId = userId;
        this.toAddress = toAddress;
        this.amountWei = amountWei;
        this.status = WithdrawalStatus.EN_PROCESO;
    }

    public void complete(String txHash) {
        this.txHash = txHash;
        this.status = WithdrawalStatus.COMPLETADO;
    }

    public void fail() {
        this.status = WithdrawalStatus.FALLIDO;
    }
}

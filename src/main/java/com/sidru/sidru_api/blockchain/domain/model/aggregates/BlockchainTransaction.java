package com.sidru.sidru_api.blockchain.domain.model.aggregates;

import com.sidru.sidru_api.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "blockchain_transactions")
public class BlockchainTransaction extends AuditableAbstractAggregateRoot<BlockchainTransaction> {

    // unique: one blockchain transaction per session (DB-level idempotency guard, RNF-BC-07).
    @Column(name = "session_id", nullable = false, unique = true)
    private Long sessionId;

    /** Citizen id owning this transaction (denormalized for wallet history queries). */
    @Column(name = "user_id")
    private Long userId;

    @Column(unique = true, nullable = false, length = 256)
    private String txHash;

    @Column(nullable = false, length = 100)
    private String network;

    @Column(nullable = false)
    private boolean confirmed;

    @Column(length = 1000)
    private String rawPayload;

    public BlockchainTransaction() {}

    public BlockchainTransaction(Long sessionId, String txHash, String network,
                                 boolean confirmed, String rawPayload) {
        this.sessionId = sessionId;
        this.txHash = txHash;
        this.network = network;
        this.confirmed = confirmed;
        this.rawPayload = rawPayload;
    }

    public BlockchainTransaction(Long sessionId, Long userId, String txHash, String network,
                                 boolean confirmed, String rawPayload) {
        this(sessionId, txHash, network, confirmed, rawPayload);
        this.userId = userId;
    }
}

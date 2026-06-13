package com.sidru.sidru_api.rewards.domain.model.aggregates;

import com.sidru.sidru_api.rewards.domain.model.valueobjects.TransactionType;
import com.sidru.sidru_api.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "point_transactions")
public class PointTransaction extends AuditableAbstractAggregateRoot<PointTransaction> {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private TransactionType type;

    @Column(nullable = false)
    private int points;

    @Column(length = 300)
    private String description;

    @Column(name = "session_id")
    private Long sessionId;

    @Column(name = "reward_id")
    private Long rewardId;

    /** Burn tx hash on Amoy when a redemption is mirrored on-chain (null otherwise). */
    @Column(name = "blockchain_tx_hash", length = 256)
    private String blockchainTxHash;

    public PointTransaction() {}

    public PointTransaction(Long userId, TransactionType type, int points, String description) {
        this.userId = userId;
        this.type = type;
        this.points = points;
        this.description = description;
    }

    public static PointTransaction earn(Long userId, int points, Long sessionId) {
        var tx = new PointTransaction(userId, TransactionType.EARN, points, "Earned from recycling session");
        tx.setSessionId(sessionId);
        return tx;
    }

    public static PointTransaction redeem(Long userId, int points, Long rewardId, String rewardName) {
        var tx = new PointTransaction(userId, TransactionType.REDEEM, points, "Redeemed: " + rewardName);
        tx.setRewardId(rewardId);
        return tx;
    }

    /** Links the on-chain burn tx that mirrored this redemption (US-20 AC2). */
    public void attachBlockchainTx(String txHash) {
        this.blockchainTxHash = txHash;
    }
}

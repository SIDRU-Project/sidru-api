package com.sidru.sidru_api.sessions.domain.model.aggregates;

import com.sidru.sidru_api.sessions.domain.model.valueobjects.SessionStatus;
import com.sidru.sidru_api.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "recycling_sessions")
public class RecyclingSession extends AuditableAbstractAggregateRoot<RecyclingSession> {

    @NotNull
    @Column(name = "smart_bin_id", nullable = false)
    private Long smartBinId;

    /** Null while session is PENDING (no citizen yet). */
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private int capCount;

    @Column(nullable = false)
    private double weightGrams;

    @Column(nullable = false)
    private int pointsEarned;

    @Column(unique = true, nullable = false, length = 64)
    private String qrToken;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private SessionStatus status;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime confirmedAt;

    @Column(length = 256)
    private String blockchainTxHash;

    public RecyclingSession() {
        this.status = SessionStatus.PENDING;
    }

    public RecyclingSession(Long smartBinId, int capCount, double weightGrams,
                            int pointsEarned, LocalDateTime expiresAt) {
        this();
        this.smartBinId = smartBinId;
        this.capCount = capCount;
        this.weightGrams = weightGrams;
        this.pointsEarned = pointsEarned;
        this.expiresAt = expiresAt;
        this.qrToken = generateQrToken();
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isPending() {
        return status == SessionStatus.PENDING;
    }

    public void confirm(Long userId) {
        this.userId = userId;
        this.status = SessionStatus.CONFIRMED;
        this.confirmedAt = LocalDateTime.now();
    }

    public void expire() {
        this.status = SessionStatus.EXPIRED;
    }

    public void cancel() {
        this.status = SessionStatus.CANCELLED;
    }

    public void attachBlockchainTx(String txHash) {
        this.blockchainTxHash = txHash;
    }

    private static String generateQrToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}

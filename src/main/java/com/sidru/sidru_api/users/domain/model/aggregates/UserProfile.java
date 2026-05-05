package com.sidru.sidru_api.users.domain.model.aggregates;

import com.sidru.sidru_api.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "user_profiles")
public class UserProfile extends AuditableAbstractAggregateRoot<UserProfile> {

    @NotNull
    @Column(name = "user_id", unique = true, nullable = false)
    private Long userId;

    @Column(length = 200)
    private String fullName;

    @Column(length = 20)
    private String phone;

    @Column(length = 200)
    private String district;

    @Column(nullable = false)
    private int totalPoints = 0;

    @Column(nullable = false)
    private int totalCaps = 0;

    @Column(nullable = false)
    private int totalSessions = 0;

    public UserProfile() {}

    public UserProfile(Long userId, String fullName, String phone, String district) {
        this.userId = userId;
        this.fullName = fullName;
        this.phone = phone;
        this.district = district;
    }

    public void addPoints(int points) {
        this.totalPoints += points;
    }

    public void subtractPoints(int points) {
        this.totalPoints -= points;
    }

    public void addCaps(int caps) {
        this.totalCaps += caps;
    }

    public void incrementSessions() {
        this.totalSessions++;
    }
}

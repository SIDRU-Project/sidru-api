package com.sidru.sidru_api.devices.domain.model.aggregates;

import com.sidru.sidru_api.devices.domain.model.valueobjects.BinStatus;
import com.sidru.sidru_api.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "smart_bins")
public class SmartBin extends AuditableAbstractAggregateRoot<SmartBin> {

    @NotBlank
    @Size(max = 100)
    @Column(unique = true, nullable = false)
    private String deviceCode;

    @NotBlank
    @Size(max = 200)
    @Column(nullable = false)
    private String location;

    @Size(max = 50)
    private String district;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private BinStatus status;

    @NotBlank
    @Column(unique = true, nullable = false, length = 256)
    private String apiKey;

    @Column(nullable = false)
    private int totalCapsCollected = 0;

    public SmartBin() {
        this.status = BinStatus.ACTIVE;
    }

    public SmartBin(String deviceCode, String location, String district) {
        this();
        this.deviceCode = deviceCode;
        this.location = location;
        this.district = district;
        this.apiKey = generateApiKey();
    }

    public void addCaps(int caps) {
        this.totalCapsCollected += caps;
    }

    private static String generateApiKey() {
        return "SIDRU-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }
}

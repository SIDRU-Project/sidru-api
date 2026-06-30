package com.sidru.sidru_api.devices.domain.model.aggregates;

import com.sidru.sidru_api.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Log/telemetría que un Smart Bin (ESP32) publica por MQTT en el tópico de eventos (US-30).
 * Es solo para diagnóstico/monitoreo: no participa del flujo de negocio. {@code createdAt}
 * (heredado) actúa como instante de recepción.
 */
@Getter
@Setter
@Entity
@Table(name = "device_logs")
public class DeviceLog extends AuditableAbstractAggregateRoot<DeviceLog> {

    @Column(nullable = false, length = 64)
    private String deviceCode;

    @Column(length = 32)
    private String type;

    @Column(length = 512)
    private String detail;

    @Column(length = 1024)
    private String rawPayload;

    protected DeviceLog() {}

    public DeviceLog(String deviceCode, String type, String detail, String rawPayload) {
        this.deviceCode = deviceCode;
        this.type = type;
        this.detail = detail;
        this.rawPayload = rawPayload;
    }
}

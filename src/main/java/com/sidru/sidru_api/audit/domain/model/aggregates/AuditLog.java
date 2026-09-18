package com.sidru.sidru_api.audit.domain.model.aggregates;

import com.sidru.sidru_api.audit.domain.model.valueobjects.AuditEventType;
import com.sidru.sidru_api.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * Asiento del log de auditoria de seguridad (CP008 / US-15, US-28).
 *
 * Registra el intento, nunca la credencial: {@code subject} guarda solo una huella
 * (prefijo + longitud) de la API key presentada, jamas la clave en claro. El instante
 * del intento es {@code createdAt}, heredado del agregado auditable.
 *
 * La tabla fisica es {@code audit_logs} (la estrategia de naming del proyecto pluraliza
 * en snake_case, igual que {@code device_logs} o {@code recycling_sessions}).
 */
@Getter
@Entity
@Table(name = "audit_logs")
public class AuditLog extends AuditableAbstractAggregateRoot<AuditLog> {

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private AuditEventType eventType;

    /** Huella del sujeto del intento (p. ej. "SB-9***" o "apikey:ab12****(32)"). Nunca la credencial. */
    @Column(nullable = false, length = 128)
    private String subject;

    /** IP de origen de la peticion, tal como la ve el backend. */
    @Column(length = 64)
    private String origin;

    /** Recurso al que se intento acceder (metodo + ruta). */
    @Column(length = 256)
    private String detail;

    protected AuditLog() {}

    public AuditLog(AuditEventType eventType, String subject, String origin, String detail) {
        this.eventType = eventType;
        this.subject = subject;
        this.origin = origin;
        this.detail = detail;
    }
}

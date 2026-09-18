package com.sidru.sidru_api.audit.interfaces.rest.resources;

import java.time.LocalDateTime;

public record AuditLogResource(
        Long id,
        String eventType,
        String subject,
        String origin,
        String detail,
        LocalDateTime occurredAt) {
}

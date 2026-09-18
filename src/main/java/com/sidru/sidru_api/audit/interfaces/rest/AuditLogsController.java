package com.sidru.sidru_api.audit.interfaces.rest;

import com.sidru.sidru_api.audit.infrastructure.persistence.jpa.repositories.AuditLogRepository;
import com.sidru.sidru_api.audit.interfaces.rest.resources.AuditLogResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Consulta del log de auditoria de seguridad (CP008 / US-28). Solo administradores:
 * los asientos describen intentos fallidos y no deben ser visibles para el ciudadano.
 */
@RestController
@RequestMapping(value = "/audit-logs", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Audit", description = "Log de auditoria de seguridad (solo administradores)")
public class AuditLogsController {

    private final AuditLogRepository repository;

    public AuditLogsController(AuditLogRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Ultimos 100 asientos de auditoria, del mas reciente al mas antiguo")
    public ResponseEntity<List<AuditLogResource>> recent() {
        return ResponseEntity.ok(repository.findTop100ByOrderByIdDesc().stream()
                .map(l -> new AuditLogResource(
                        l.getId(),
                        l.getEventType().name(),
                        l.getSubject(),
                        l.getOrigin(),
                        l.getDetail(),
                        l.getCreatedAt()))
                .toList());
    }
}

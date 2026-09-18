package com.sidru.sidru_api.audit.application.internal.commandservices;

import com.sidru.sidru_api.audit.domain.model.aggregates.AuditLog;
import com.sidru.sidru_api.audit.domain.model.valueobjects.AuditEventType;
import com.sidru.sidru_api.audit.infrastructure.persistence.jpa.repositories.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Escribe asientos de auditoria de seguridad (CP008 / US-15, US-28).
 *
 * {@code REQUIRES_NEW}: el asiento debe sobrevivir aunque la transaccion de negocio que
 * disparo el rechazo se marque para rollback. Auditar nunca debe tumbar la peticion, por
 * eso el registro es best-effort.
 */
@Service
public class AuditLogService {

    private final AuditLogRepository repository;

    public AuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog record(AuditEventType eventType, String subject, String origin, String detail) {
        return repository.save(new AuditLog(eventType, subject, origin, detail));
    }

    /**
     * Enmascara una credencial para dejarla en el log: prefijo de 4 caracteres + longitud.
     * Nunca se persiste la clave completa (regla dura de secretos del proyecto).
     */
    public static String fingerprint(String credential) {
        if (credential == null || credential.isBlank()) return "apikey:(vacia)";
        String prefix = credential.length() <= 4 ? credential : credential.substring(0, 4);
        return "apikey:" + prefix + "****(" + credential.length() + ")";
    }
}

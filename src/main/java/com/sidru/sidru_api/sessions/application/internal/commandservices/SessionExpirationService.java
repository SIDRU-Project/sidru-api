package com.sidru.sidru_api.sessions.application.internal.commandservices;

import com.sidru.sidru_api.sessions.infrastructure.persistence.jpa.repositories.RecyclingSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Marca como EXPIRED una sesion cuyo QR vencio (CP015 / US-23).
 *
 * <p>Vive fuera del flujo de canje a proposito. El canje corre en una transaccion que se
 * revierte al lanzar {@code SessionExpiredException}, asi que marcar ahi dentro no persiste
 * nada; y hacerlo en una transaccion anidada chocaria con el lock pesimista que esa misma
 * transaccion mantiene sobre la fila. Se invoca desde el advice, cuando la transaccion de
 * canje ya termino y libero el lock.</p>
 *
 * <p>{@code REQUIRES_NEW} + idempotente: si la sesion ya no esta PENDING no toca nada.</p>
 */
@Service
public class SessionExpirationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionExpirationService.class);

    private final RecyclingSessionRepository sessionRepository;

    public SessionExpirationService(RecyclingSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markExpired(Long sessionId) {
        if (sessionId == null) return;
        sessionRepository.findById(sessionId).ifPresent(session -> {
            if (!session.isPending()) return;
            session.expire();
            sessionRepository.save(session);
            LOGGER.info("Sesion {} marcada como EXPIRED tras un intento de canje fuera de vigencia", sessionId);
        });
    }
}

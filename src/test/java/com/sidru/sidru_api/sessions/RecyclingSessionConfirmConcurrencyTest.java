package com.sidru.sidru_api.sessions;

import com.sidru.sidru_api.sessions.application.internal.commandservices.RecyclingSessionCommandServiceImpl;
import com.sidru.sidru_api.sessions.application.internal.outboundservices.acl.ExternalDevicesService;
import com.sidru.sidru_api.sessions.application.internal.outboundservices.acl.ExternalUserProfileService;
import com.sidru.sidru_api.sessions.application.internal.outboundservices.blockchain.BlockchainPort;
import com.sidru.sidru_api.sessions.domain.model.aggregates.RecyclingSession;
import com.sidru.sidru_api.sessions.domain.model.commands.ConfirmRecyclingSessionCommand;
import com.sidru.sidru_api.sessions.domain.model.exceptions.InvalidSessionStateException;
import com.sidru.sidru_api.sessions.domain.model.valueobjects.SessionStatus;
import com.sidru.sidru_api.sessions.infrastructure.persistence.jpa.repositories.RecyclingSessionRepository;
import com.sidru.sidru_api.shared.domain.model.events.SessionConfirmedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Canje único / anti-doble-canje (US-23). El handler de confirmación debe:
 * (1) leer la sesión con el finder de bloqueo PESIMISTA ({@code findByQrTokenForUpdate}),
 *     que serializa confirmaciones concurrentes sobre el mismo QR;
 * (2) rechazar con {@link InvalidSessionStateException} cualquier sesión que ya NO esté
 *     PENDING (la 2ª confirmación concurrente la lee CONFIRMED) sin volver a acreditar
 *     puntos ni mintear;
 * (3) en el camino feliz, acreditar puntos/caps, registrar en blockchain y publicar el
 *     evento de confirmación exactamente una vez.
 * Todo mockeado (sin DB ni red).
 */
class RecyclingSessionConfirmConcurrencyTest {

    private RecyclingSessionRepository sessionRepository;
    private ExternalDevicesService externalDevicesService;
    private ExternalUserProfileService externalUserProfileService;
    private BlockchainPort blockchainPort;
    private ApplicationEventPublisher eventPublisher;
    private RecyclingSessionCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        sessionRepository = mock(RecyclingSessionRepository.class);
        externalDevicesService = mock(ExternalDevicesService.class);
        externalUserProfileService = mock(ExternalUserProfileService.class);
        blockchainPort = mock(BlockchainPort.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new RecyclingSessionCommandServiceImpl(
                sessionRepository, externalDevicesService, externalUserProfileService,
                blockchainPort, eventPublisher);
    }

    private RecyclingSession pendingSession() {
        return new RecyclingSession(3L, 1, 25.0, 10, LocalDateTime.now().plusMinutes(15));
    }

    @Test
    void confirmaUnaSesionPendingLeyendoConBloqueoPesimista() {
        var session = pendingSession();
        when(sessionRepository.findByQrTokenForUpdate(session.getQrToken()))
                .thenReturn(Optional.of(session));
        when(blockchainPort.recordSession(session)).thenReturn(Optional.of("0xabc"));
        when(sessionRepository.save(session)).thenReturn(session);

        var result = service.handle(new ConfirmRecyclingSessionCommand(session.getQrToken(), 6L));

        assertTrue(result.isPresent());
        assertEquals(SessionStatus.CONFIRMED, session.getStatus());
        // Debe leer con el finder con lock pesimista, NO con el plano.
        verify(sessionRepository).findByQrTokenForUpdate(session.getQrToken());
        verify(sessionRepository, never()).findByQrToken(any());
        verify(externalDevicesService).addCaps(session.getSmartBinId(), session.getCapCount());
        verify(externalUserProfileService)
                .addPointsAndCaps(6L, session.getPointsEarned(), session.getCapCount());
        verify(eventPublisher).publishEvent(any(SessionConfirmedEvent.class));
        verify(sessionRepository).save(session);
    }

    @Test
    void rechazaLaSegundaConfirmacionConcurrenteSinReacreditar() {
        // La 1ª confirmación ya dejó la sesión en CONFIRMED; la 2ª (concurrente) la lee así.
        var session = pendingSession();
        session.confirm(6L); // -> CONFIRMED
        when(sessionRepository.findByQrTokenForUpdate(session.getQrToken()))
                .thenReturn(Optional.of(session));

        var cmd = new ConfirmRecyclingSessionCommand(session.getQrToken(), 7L);
        assertThrows(InvalidSessionStateException.class, () -> service.handle(cmd));

        // No vuelve a acreditar puntos/caps, ni mintea, ni publica el evento.
        verifyNoInteractions(externalDevicesService, externalUserProfileService,
                blockchainPort, eventPublisher);
        verify(sessionRepository, never()).save(any());
    }
}

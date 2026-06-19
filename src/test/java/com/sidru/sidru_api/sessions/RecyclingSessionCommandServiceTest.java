package com.sidru.sidru_api.sessions;

import com.sidru.sidru_api.sessions.application.internal.commandservices.RecyclingSessionCommandServiceImpl;
import com.sidru.sidru_api.sessions.application.internal.outboundservices.acl.ExternalDevicesService;
import com.sidru.sidru_api.sessions.application.internal.outboundservices.acl.ExternalUserProfileService;
import com.sidru.sidru_api.sessions.application.internal.outboundservices.blockchain.BlockchainPort;
import com.sidru.sidru_api.sessions.domain.model.aggregates.RecyclingSession;
import com.sidru.sidru_api.sessions.domain.model.commands.CancelRecyclingSessionCommand;
import com.sidru.sidru_api.sessions.domain.model.commands.CreateRecyclingSessionCommand;
import com.sidru.sidru_api.sessions.domain.model.exceptions.InvalidCapCountException;
import com.sidru.sidru_api.sessions.domain.model.exceptions.InvalidSessionStateException;
import com.sidru.sidru_api.sessions.domain.model.exceptions.InvalidSessionWeightException;
import com.sidru.sidru_api.sessions.domain.model.exceptions.RecyclingSessionNotFoundException;
import com.sidru.sidru_api.sessions.domain.model.exceptions.UnauthorizedDeviceException;
import com.sidru.sidru_api.sessions.domain.model.valueobjects.SessionStatus;
import com.sidru.sidru_api.sessions.infrastructure.persistence.jpa.repositories.RecyclingSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Creación y cancelación de sesiones (US-15/US-18/US-33). Cubre el cálculo de puntos por peso,
 * las validaciones (dispositivo no autorizado, capCount y peso fuera de rango) y la cancelación
 * (camino feliz, estado inválido, no encontrada). Mockeado; los @Value se inyectan por reflexión.
 */
class RecyclingSessionCommandServiceTest {

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
        ReflectionTestUtils.setField(service, "pricePerKgSoles", 4.00);
        ReflectionTestUtils.setField(service, "pointsPerSol", 100);
        ReflectionTestUtils.setField(service, "minWeightGrams", 1.0);
        ReflectionTestUtils.setField(service, "maxWeightGrams", 50000.0);
        ReflectionTestUtils.setField(service, "sessionExpiryMinutes", 15);
        ReflectionTestUtils.setField(service, "minCaps", 1);
        ReflectionTestUtils.setField(service, "maxCaps", 500);
    }

    @Test
    void creaUnaSesionYCalculaLosPuntosPorPeso() {
        when(externalDevicesService.resolveSmartBinIdByApiKey("key")).thenReturn(5L);
        when(sessionRepository.save(any(RecyclingSession.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = service.handle(new CreateRecyclingSessionCommand("key", 10, 1000.0));

        assertTrue(result.isPresent());
        // 1000 g = 1 kg * 4.00 S/ * 100 pts/sol = 400 puntos
        assertEquals(400, result.get().getPointsEarned());
        assertEquals(SessionStatus.PENDING, result.get().getStatus());
        verify(sessionRepository).save(any(RecyclingSession.class));
    }

    @Test
    void rechazaDispositivoNoAutorizado() {
        when(externalDevicesService.resolveSmartBinIdByApiKey("bad")).thenReturn(0L);
        assertThrows(UnauthorizedDeviceException.class,
                () -> service.handle(new CreateRecyclingSessionCommand("bad", 10, 1000.0)));
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void rechazaCapCountFueraDeRango() {
        when(externalDevicesService.resolveSmartBinIdByApiKey("key")).thenReturn(5L);
        assertThrows(InvalidCapCountException.class,
                () -> service.handle(new CreateRecyclingSessionCommand("key", 9999, 1000.0)));
    }

    @Test
    void rechazaPesoFueraDeRango() {
        when(externalDevicesService.resolveSmartBinIdByApiKey("key")).thenReturn(5L);
        assertThrows(InvalidSessionWeightException.class,
                () -> service.handle(new CreateRecyclingSessionCommand("key", 10, 999999.0)));
    }

    @Test
    void cancelaUnaSesionPending() {
        var session = new RecyclingSession(3L, 1, 25.0, 10, LocalDateTime.now().plusMinutes(15));
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(sessionRepository.save(session)).thenReturn(session);

        var result = service.handle(new CancelRecyclingSessionCommand(1L));

        assertTrue(result.isPresent());
        assertEquals(SessionStatus.CANCELLED, session.getStatus());
    }

    @Test
    void noCancelaUnaSesionNoPending() {
        var session = new RecyclingSession(3L, 1, 25.0, 10, LocalDateTime.now().plusMinutes(15));
        session.confirm(6L);  // -> CONFIRMED
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        assertThrows(InvalidSessionStateException.class,
                () -> service.handle(new CancelRecyclingSessionCommand(1L)));
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void cancelarSesionInexistenteLanzaNotFound() {
        when(sessionRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(RecyclingSessionNotFoundException.class,
                () -> service.handle(new CancelRecyclingSessionCommand(99L)));
    }
}

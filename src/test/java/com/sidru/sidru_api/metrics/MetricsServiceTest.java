package com.sidru.sidru_api.metrics;

import com.sidru.sidru_api.devices.interfaces.acl.DevicesContextFacade;
import com.sidru.sidru_api.metrics.application.internal.queryservices.MetricsService;
import com.sidru.sidru_api.metrics.interfaces.rest.resources.MetricsResource;
import com.sidru.sidru_api.sessions.interfaces.acl.SessionsContextFacade;
import com.sidru.sidru_api.users.interfaces.acl.UserProfileContextFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Agregación de métricas e impacto ambiental (US-36). Verifica que combina los agregados
 * de las fachadas y deriva CO₂/energía del peso con los factores configurados. Todo mockeado.
 */
class MetricsServiceTest {

    private SessionsContextFacade sessions;
    private UserProfileContextFacade users;
    private DevicesContextFacade devices;
    private MetricsService service;

    @BeforeEach
    void setUp() {
        sessions = mock(SessionsContextFacade.class);
        users = mock(UserProfileContextFacade.class);
        devices = mock(DevicesContextFacade.class);
        service = new MetricsService(sessions, users, devices);
        ReflectionTestUtils.setField(service, "co2KgPerKgPlastic", 1.5);
        ReflectionTestUtils.setField(service, "energyKwhPerKgPlastic", 5.8);
    }

    @Test
    void agregaTotalesYCalculaElImpactoPorPeso() {
        when(sessions.countAllSessions()).thenReturn(20L);
        when(sessions.countConfirmedSessions()).thenReturn(12L);
        when(sessions.sumConfirmedCaps()).thenReturn(480L);
        when(sessions.sumConfirmedWeightGrams()).thenReturn(2000.0);  // 2 kg
        when(sessions.sumConfirmedPoints()).thenReturn(800L);
        when(users.countUsers()).thenReturn(7L);
        when(devices.countBins()).thenReturn(3L);

        MetricsResource m = service.gather();

        assertEquals(20L, m.totalSessions());
        assertEquals(12L, m.confirmedSessions());
        assertEquals(480L, m.capsRecycled());
        assertEquals(2.0, m.weightKg());
        assertEquals(800L, m.ctcMinted());
        assertEquals(7L, m.registeredUsers());
        assertEquals(3L, m.activeBins());
        // 2 kg * 1.5 = 3.0 kg CO2 evitado; 2 kg * 5.8 = 11.6 kWh ahorrados.
        assertEquals(3.0, m.impact().co2AvoidedKg());
        assertEquals(11.6, m.impact().energySavedKwh());
    }
}

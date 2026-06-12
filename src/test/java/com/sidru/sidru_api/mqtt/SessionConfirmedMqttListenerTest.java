package com.sidru.sidru_api.mqtt;

import com.sidru.sidru_api.devices.interfaces.acl.DevicesContextFacade;
import com.sidru.sidru_api.mqtt.application.internal.eventhandlers.SessionConfirmedMqttListener;
import com.sidru.sidru_api.mqtt.application.internal.outboundservices.MqttPort;
import com.sidru.sidru_api.shared.domain.model.events.SessionConfirmedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests (no DB, no broker) for {@link SessionConfirmedMqttListener} (US-IOT-05 / US-IOT-07).
 * Collaborators are mocked.
 */
class SessionConfirmedMqttListenerTest {

    private static final Long SMART_BIN_ID = 1L;
    private static final Long USER_ID = 3L;
    private static final String DEVICE_CODE = "BIN-001";

    private MqttPort mqttPort;
    private DevicesContextFacade devicesContextFacade;
    private SessionConfirmedMqttListener listener;

    @BeforeEach
    void setUp() {
        mqttPort = mock(MqttPort.class);
        devicesContextFacade = mock(DevicesContextFacade.class);
        listener = new SessionConfirmedMqttListener(mqttPort, devicesContextFacade);
    }

    @Test
    void resuelveDeviceCodeYPublicaOpen() {
        when(devicesContextFacade.fetchDeviceCodeById(SMART_BIN_ID)).thenReturn(DEVICE_CODE);

        listener.on(new SessionConfirmedEvent(SMART_BIN_ID, USER_ID));

        verify(mqttPort, times(1)).sendOpen(eq(DEVICE_CODE));
    }

    @Test
    void sinDeviceCode_noPublica() {
        when(devicesContextFacade.fetchDeviceCodeById(SMART_BIN_ID)).thenReturn(null);

        listener.on(new SessionConfirmedEvent(SMART_BIN_ID, USER_ID));

        verify(mqttPort, never()).sendOpen(anyString());
    }

    @Test
    void falloDelBroker_noPropagaExcepcion() {
        when(devicesContextFacade.fetchDeviceCodeById(SMART_BIN_ID)).thenReturn(DEVICE_CODE);
        doThrow(new RuntimeException("broker down")).when(mqttPort).sendOpen(any());

        // Best-effort: la confirmación de la sesión nunca debe romperse por un fallo de MQTT.
        assertDoesNotThrow(() -> listener.on(new SessionConfirmedEvent(SMART_BIN_ID, USER_ID)));

        verify(mqttPort, times(1)).sendOpen(eq(DEVICE_CODE));
    }
}

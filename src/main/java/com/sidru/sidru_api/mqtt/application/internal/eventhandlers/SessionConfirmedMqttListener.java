package com.sidru.sidru_api.mqtt.application.internal.eventhandlers;

import com.sidru.sidru_api.devices.interfaces.acl.DevicesContextFacade;
import com.sidru.sidru_api.mqtt.application.internal.outboundservices.MqttPort;
import com.sidru.sidru_api.shared.domain.model.events.SessionConfirmedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Ante una sesión confirmada, publica OPEN al Smart Bin de origen por MQTT
 * (US-IOT-05 / US-IOT-07). Solo activo con {@code sidru.mqtt.enabled=true}.
 *
 * <p>Así {@code sessions} no conoce MQTT: solo publica un {@link SessionConfirmedEvent}
 * y aquí se resuelve el dispositivo.
 */
@Component
@ConditionalOnProperty(name = "sidru.mqtt.enabled", havingValue = "true")
public class SessionConfirmedMqttListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionConfirmedMqttListener.class);

    private final MqttPort mqttPort;
    private final DevicesContextFacade devicesContextFacade;

    public SessionConfirmedMqttListener(MqttPort mqttPort, DevicesContextFacade devicesContextFacade) {
        this.mqttPort = mqttPort;
        this.devicesContextFacade = devicesContextFacade;
    }

    @EventListener
    public void on(SessionConfirmedEvent event) {
        String deviceCode = devicesContextFacade.fetchDeviceCodeById(event.smartBinId());
        if (deviceCode == null) {
            LOGGER.warn("Session confirmed for smartBinId={} but no deviceCode resolved — skipping OPEN",
                    event.smartBinId());
            return;
        }
        try {
            mqttPort.sendOpen(deviceCode);
            LOGGER.info("Published OPEN to bin {} after session confirmation (user {})",
                    deviceCode, event.userId());
        } catch (Exception ex) {
            // Best-effort: un fallo del broker no debe romper la confirmación de la sesión.
            LOGGER.error("Failed to publish OPEN to bin {}: {}", deviceCode, ex.getMessage(), ex);
        }
    }
}

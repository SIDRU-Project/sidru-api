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
 * Reacts to a confirmed recycling session by pushing an OPEN command to the originating Smart Bin
 * over MQTT (US-IOT-05 / US-IOT-07). Only active when {@code sidru.mqtt.enabled=true}.
 *
 * <p>This keeps the {@code sessions} context unaware of MQTT: it merely publishes a neutral
 * {@link SessionConfirmedEvent}; the {@code mqtt} context resolves the device and publishes.
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
            // Best-effort: a broker failure must never break the session confirmation flow.
            LOGGER.error("Failed to publish OPEN to bin {}: {}", deviceCode, ex.getMessage(), ex);
        }
    }
}

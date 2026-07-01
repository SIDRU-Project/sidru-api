package com.sidru.sidru_api.mqtt.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sidru.sidru_api.devices.interfaces.acl.DevicesContextFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

/**
 * Consume los eventos/logs que los Smart Bins publican en sidru/bin/+/events (US-30),
 * los registra en el log del backend y los persiste vía el contexto devices (ACL).
 * El deviceCode se extrae del tópico (fiable); el cuerpo JSON aporta type/detail.
 */
@Component
@ConditionalOnProperty(name = "sidru.mqtt.enabled", havingValue = "true")
public class DeviceEventsInboundHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeviceEventsInboundHandler.class);

    private final DevicesContextFacade devicesContextFacade;
    private final ObjectMapper objectMapper;

    public DeviceEventsInboundHandler(DevicesContextFacade devicesContextFacade, ObjectMapper objectMapper) {
        this.devicesContextFacade = devicesContextFacade;
        this.objectMapper = objectMapper;
    }

    @ServiceActivator(inputChannel = "mqttInboundChannel")
    public void handle(Message<String> message) {
        String topic = (String) message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC);
        String payload = message.getPayload();
        String deviceCode = extractDeviceCode(topic);

        String type = "event";
        String detail = "";
        try {
            JsonNode node = objectMapper.readTree(payload);
            type = node.path("type").asText("event");
            detail = node.path("detail").asText("");
        } catch (Exception e) {
            LOGGER.warn("Evento MQTT no-JSON de {}: {}", deviceCode, payload);
        }

        LOGGER.info("[DEVICE {}] {} {}", deviceCode, type, detail);
        // Acota a la longitud de la columna para no fallar al persistir.
        String safePayload = payload != null && payload.length() > 1024 ? payload.substring(0, 1024) : payload;
        devicesContextFacade.recordDeviceLog(deviceCode, type, detail, safePayload);
    }

    /** Tópico = sidru/bin/{deviceCode}/events → posición 2. */
    private static String extractDeviceCode(String topic) {
        if (topic == null) return "unknown";
        String[] parts = topic.split("/");
        return parts.length >= 3 ? parts[2] : "unknown";
    }
}

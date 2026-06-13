package com.sidru.sidru_api.mqtt.infrastructure.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sidru.sidru_api.mqtt.application.internal.outboundservices.MqttPort;
import com.sidru.sidru_api.mqtt.infrastructure.gateway.MqttGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@ConditionalOnProperty(name = "sidru.mqtt.enabled", havingValue = "true")
public class PahoMqttAdapter implements MqttPort {

    private static final Logger LOGGER = LoggerFactory.getLogger(PahoMqttAdapter.class);

    private final MqttGateway mqttGateway;
    private final ObjectMapper objectMapper;

    @Value("${sidru.mqtt.topic.bin-commands}")
    private String commandTopicTemplate;

    public PahoMqttAdapter(MqttGateway mqttGateway, ObjectMapper objectMapper) {
        this.mqttGateway = mqttGateway;
        this.objectMapper = objectMapper;
    }

    @Override
    public void sendCommand(String deviceCode, String command, Map<String, Object> payload) {
        String topic = commandTopicTemplate.replace("{deviceId}", deviceCode);
        try {
            Map<String, Object> message = Map.of("command", command, "payload", payload);
            mqttGateway.publish(topic, objectMapper.writeValueAsString(message));
            LOGGER.info("MQTT command '{}' sent to topic '{}'", command, topic);
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to serialize MQTT command payload", e);
        }
    }

    @Override
    public void sendReset(String deviceCode) {
        sendCommand(deviceCode, "RESET", Map.of());
    }

    @Override
    public void sendOpen(String deviceCode) {
        sendCommand(deviceCode, "OPEN", Map.of());
    }
}

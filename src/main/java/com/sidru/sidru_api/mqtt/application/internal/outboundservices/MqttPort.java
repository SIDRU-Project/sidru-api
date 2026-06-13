package com.sidru.sidru_api.mqtt.application.internal.outboundservices;

import java.util.Map;

/**
 * Outbound port to publish commands to Smart Bins via MQTT.
 * Concrete adapter lives in infrastructure.
 */
public interface MqttPort {
    void sendCommand(String deviceCode, String command, Map<String, Object> payload);
    void sendReset(String deviceCode);
    void sendOpen(String deviceCode);
}

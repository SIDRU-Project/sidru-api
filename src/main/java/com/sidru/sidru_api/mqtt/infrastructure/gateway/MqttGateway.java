package com.sidru.sidru_api.mqtt.infrastructure.gateway;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.integration.annotation.MessagingGateway;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.handler.annotation.Header;

@MessagingGateway(defaultRequestChannel = "mqttOutboundChannel")
@ConditionalOnProperty(name = "sidru.mqtt.enabled", havingValue = "true")
public interface MqttGateway {
    void publish(@Header(MqttHeaders.TOPIC) String topic, String payload);
}

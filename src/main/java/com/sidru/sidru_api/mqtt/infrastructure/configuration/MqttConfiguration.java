package com.sidru.sidru_api.mqtt.infrastructure.configuration;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

@Configuration
@ConditionalOnProperty(name = "sidru.mqtt.enabled", havingValue = "true")
public class MqttConfiguration {

    @Value("${sidru.mqtt.broker-url}")
    private String brokerUrl;

    @Value("${sidru.mqtt.client-id}")
    private String clientId;

    @Value("${sidru.mqtt.username}")
    private String username;

    @Value("${sidru.mqtt.password}")
    private String password;

    @Value("${sidru.mqtt.topic.bin-events}")
    private String eventsTopic;   // sidru/bin/+/events

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{brokerUrl});
        options.setUserName(username);
        options.setPassword(password.toCharArray());
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);
        factory.setConnectionOptions(options);
        return factory;
    }

    @Bean
    public MessageChannel mqttOutboundChannel() {
        return new DirectChannel();
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttOutboundChannel")
    public MessageHandler mqttOutboundHandler(MqttPahoClientFactory factory) {
        MqttPahoMessageHandler handler = new MqttPahoMessageHandler(clientId + "-pub", factory);
        handler.setAsync(true);
        handler.setDefaultQos(1);
        return handler;
    }

    // ───────────────────────── Inbound: logs de dispositivos (US-30) ─────────────────────────
    @Bean
    public MessageChannel mqttInboundChannel() {
        return new DirectChannel();
    }

    /**
     * Suscriptor a sidru/bin/+/events: empuja cada evento al canal inbound, donde lo
     * recoge {@code DeviceEventsInboundHandler}. La salida (payload String + header con el
     * tópico) la consume el @ServiceActivator de ese handler.
     */
    @Bean
    public MqttPahoMessageDrivenChannelAdapter mqttInboundAdapter(MqttPahoClientFactory factory) {
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(clientId + "-sub", factory, eventsTopic);
        adapter.setCompletionTimeout(5000);
        adapter.setQos(1);
        adapter.setOutputChannel(mqttInboundChannel());
        return adapter;
    }
}

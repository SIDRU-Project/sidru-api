package com.sidru.sidru_api.notifications.infrastructure.fcm;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.sidru.sidru_api.notifications.application.internal.outboundservices.fcm.NotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Envía push reales por el Firebase Admin SDK cuando {@code sidru.firebase.enabled=true}
 * y existe el bean {@link FirebaseMessaging} (de {@link FirebaseConfig}); si no, queda
 * no-op para no afectar al resto del flujo (push best-effort, US-39). Empuja a tópicos
 * por usuario {@code user-{userId}}, así no hace falta registro de device tokens.
 */
@Service
public class FcmNotificationAdapter implements NotificationPort {

    private static final Logger LOGGER = LoggerFactory.getLogger(FcmNotificationAdapter.class);

    private final ObjectProvider<FirebaseMessaging> messagingProvider;
    private final boolean enabled;

    public FcmNotificationAdapter(ObjectProvider<FirebaseMessaging> messagingProvider,
                                  @Value("${sidru.firebase.enabled:false}") boolean enabled) {
        this.messagingProvider = messagingProvider;
        this.enabled = enabled;
    }

    @Override
    public void sendToDevice(String fcmToken, String title, String body) {
        FirebaseMessaging messaging = resolveMessaging();
        if (messaging == null) {
            return;
        }
        Message message = Message.builder()
                .setToken(fcmToken)
                .setNotification(buildNotification(title, body))
                .build();
        send(messaging, message, "device " + fcmToken);
    }

    @Override
    public void sendToTopic(String topic, String title, String body) {
        FirebaseMessaging messaging = resolveMessaging();
        if (messaging == null) {
            return;
        }
        Message message = Message.builder()
                .setTopic(topic)
                .setNotification(buildNotification(title, body))
                .build();
        send(messaging, message, "topic " + topic);
    }

    private Notification buildNotification(String title, String body) {
        return Notification.builder().setTitle(title).setBody(body).build();
    }

    /** Best-effort: un fallo de envío se loguea pero no se propaga (no rompe al que llama). */
    private void send(FirebaseMessaging messaging, Message message, String target) {
        try {
            String messageId = messaging.send(message);
            LOGGER.info("FCM push sent to {} (messageId={})", target, messageId);
        } catch (Exception ex) {
            // Stack completo para ver la causa real (auth, SSL, etc.).
            LOGGER.error("FCM push to {} failed: {}", target, ex.getMessage(), ex);
        }
    }

    /** Devuelve el cliente si el push está activo, o {@code null} (no-op) si está apagado o mal configurado. */
    private FirebaseMessaging resolveMessaging() {
        if (!enabled) {
            LOGGER.debug("FCM disabled — skipping push");
            return null;
        }
        FirebaseMessaging messaging = messagingProvider.getIfAvailable();
        if (messaging == null) {
            LOGGER.warn("FCM enabled but no FirebaseMessaging bean — check the service-account credentials");
        }
        return messaging;
    }
}

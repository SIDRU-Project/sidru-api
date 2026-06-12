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
 * Firebase Cloud Messaging adapter.
 *
 * <p>Sends real push notifications via the Firebase Admin SDK when
 * {@code sidru.firebase.enabled=true} and a {@link FirebaseMessaging} bean is available
 * (provided by {@link FirebaseConfig}). Otherwise it degrades to a safe no-op so the rest
 * of the system (mint confirmation, reward redemption) is never affected — push delivery
 * is best-effort by design (US-39).
 *
 * <p>The backend pushes to per-user topics ({@code user-{userId}}); the mobile app
 * subscribes each signed-in citizen to that topic, so no device-token registry is needed.
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

    /**
     * Sends best-effort: a delivery failure is logged but never propagated, so an FCM
     * outage cannot break the caller's flow (on-chain confirmation, redemption, etc.).
     */
    private void send(FirebaseMessaging messaging, Message message, String target) {
        try {
            String messageId = messaging.send(message);
            LOGGER.info("FCM push sent to {} (messageId={})", target, messageId);
        } catch (Exception ex) {
            // Log the full stack/cause chain to surface the real root cause (auth, SSL, etc.).
            LOGGER.error("FCM push to {} failed: {}", target, ex.getMessage(), ex);
        }
    }

    /**
     * Returns the messaging client when push is active, or {@code null} (no-op) when
     * disabled or misconfigured. Never throws.
     */
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

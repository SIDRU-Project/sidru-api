package com.sidru.sidru_api.notifications.application.acl;

import com.sidru.sidru_api.notifications.application.internal.outboundservices.fcm.NotificationPort;
import com.sidru.sidru_api.notifications.interfaces.acl.NotificationContextFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Delega en el {@link NotificationPort} interno usando el tópico FCM por usuario
 * {@code user-{userId}}. La app suscribe a cada ciudadano logueado a ese tópico
 * (previsto para Sprint 2), así no hace falta guardar device tokens.
 *
 * <p>Best-effort: cualquier fallo (FCM apagado, red…) se loguea y nunca se propaga.
 */
@Service
public class NotificationContextFacadeImpl implements NotificationContextFacade {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationContextFacadeImpl.class);
    private static final String USER_TOPIC_PREFIX = "user-";

    private final NotificationPort notificationPort;

    public NotificationContextFacadeImpl(NotificationPort notificationPort) {
        this.notificationPort = notificationPort;
    }

    @Override
    public void notifyUser(Long userId, String title, String body) {
        if (userId == null) {
            LOGGER.debug("notifyUser called with null userId — skipping");
            return;
        }
        String topic = USER_TOPIC_PREFIX + userId;
        try {
            notificationPort.sendToTopic(topic, title, body);
        } catch (Exception ex) {
            // Best-effort: nunca romper el flujo del que llama.
            LOGGER.warn("Failed to notify user {} on topic {}: {}", userId, topic, ex.getMessage());
        }
    }
}

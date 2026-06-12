package com.sidru.sidru_api.notifications.application.acl;

import com.sidru.sidru_api.notifications.application.internal.outboundservices.fcm.NotificationPort;
import com.sidru.sidru_api.notifications.interfaces.acl.NotificationContextFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Implementation of the notifications ACL.
 *
 * <p>Delegates to the internal {@link NotificationPort}, targeting the per-user FCM
 * topic {@code "user-{userId}"}. Convention: the mobile app subscribes each signed-in
 * citizen to that topic (planned for Sprint 2), so the backend can push to a user
 * without persisting device tokens.
 *
 * <p>Best-effort: any failure (FCM disabled, network, etc.) is caught and logged; it
 * is never propagated, so callers such as the blockchain event listener are unaffected.
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
            // Best-effort: never break the caller's flow.
            LOGGER.warn("Failed to notify user {} on topic {}: {}", userId, topic, ex.getMessage());
        }
    }
}

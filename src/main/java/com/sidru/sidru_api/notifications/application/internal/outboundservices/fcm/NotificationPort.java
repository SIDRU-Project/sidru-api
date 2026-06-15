package com.sidru.sidru_api.notifications.application.internal.outboundservices.fcm;

/**
 * Outbound port for sending push notifications.
 * Implemented by adapters in the infrastructure layer (FCM, mock, etc.).
 */
public interface NotificationPort {
    void sendToDevice(String fcmToken, String title, String body);
    void sendToTopic(String topic, String title, String body);
}

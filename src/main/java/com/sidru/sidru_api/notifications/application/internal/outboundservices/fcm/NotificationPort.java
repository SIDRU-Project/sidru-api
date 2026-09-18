package com.sidru.sidru_api.notifications.application.internal.outboundservices.fcm;

/**
 * Puerto de salida para enviar push. Lo implementan adapters de infraestructura (FCM, mock…).
 */
public interface NotificationPort {
    void sendToDevice(String fcmToken, String title, String body);
    void sendToTopic(String topic, String title, String body);
}

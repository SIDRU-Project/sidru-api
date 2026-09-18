package com.sidru.sidru_api.notifications.interfaces.acl;

/**
 * ACL del contexto {@code notifications}: deja que otros contextos (p. ej. {@code blockchain})
 * disparen push al ciudadano sin acoplarse al {@code NotificationPort} interno ni a FCM.
 */
public interface NotificationContextFacade {

    /**
     * Push best-effort a un ciudadano. La implementación nunca debe propagar excepciones:
     * un fallo aquí no puede romper el flujo del que llama (p. ej. confirmar una tx on-chain).
     *
     * @param userId id del ciudadano (mapeado al tópico FCM {@code user-{userId}})
     */
    void notifyUser(Long userId, String title, String body);
}

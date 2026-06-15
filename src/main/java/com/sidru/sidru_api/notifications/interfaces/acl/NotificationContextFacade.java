package com.sidru.sidru_api.notifications.interfaces.acl;

/**
 * Anti-corruption layer (ACL) of the {@code notifications} bounded context.
 *
 * <p>Exposes a minimal, stable contract so other contexts (e.g. {@code blockchain})
 * can trigger user-facing push notifications without depending on the internal
 * {@code NotificationPort} or any FCM detail. This keeps inbound coupling at the
 * interface boundary, following the same pattern as
 * {@code users.interfaces.acl.UserProfileContextFacade}.
 */
public interface NotificationContextFacade {

    /**
     * Sends a best-effort push notification to a single citizen.
     *
     * <p>Implementations must never propagate exceptions: a failure here must not
     * break the caller's flow (e.g. confirming an on-chain transaction).
     *
     * @param userId citizen id (resolved to the FCM topic {@code "user-{userId}"})
     * @param title  notification title
     * @param body   notification body
     */
    void notifyUser(Long userId, String title, String body);
}

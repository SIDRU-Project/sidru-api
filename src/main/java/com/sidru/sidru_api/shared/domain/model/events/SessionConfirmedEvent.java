package com.sidru.sidru_api.shared.domain.model.events;

/**
 * Domain event published when a recycling session is confirmed. Lives in shared so the
 * publisher (sessions) and the listener (mqtt) depend on a neutral type, not on each other.
 *
 * @param smartBinId Smart Bin that originated the confirmed session
 * @param userId     citizen who confirmed the session
 */
public record SessionConfirmedEvent(Long smartBinId, Long userId) {
}

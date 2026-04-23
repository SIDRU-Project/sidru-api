package com.sidru.sidru_api.shared.domain.model.events;

/**
 * Domain integration event published when a recycling session is confirmed.
 * Lives in {@code shared} so the publishing context ({@code sessions}) and any listening
 * context ({@code mqtt}) depend on a neutral type instead of each other.
 *
 * @param smartBinId the Smart Bin that originated the confirmed session
 * @param userId     the citizen who confirmed the session
 */
public record SessionConfirmedEvent(Long smartBinId, Long userId) {
}

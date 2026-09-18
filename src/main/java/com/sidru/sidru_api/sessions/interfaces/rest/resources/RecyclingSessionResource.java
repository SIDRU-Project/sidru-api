package com.sidru.sidru_api.sessions.interfaces.rest.resources;

import com.sidru.sidru_api.sessions.domain.model.valueobjects.SessionStatus;

import java.time.LocalDateTime;

public record RecyclingSessionResource(
        Long id,
        /** Fecha de registro de la sesion; es el criterio de orden del historial (CP016). */
        LocalDateTime createdAt,
        Long smartBinId,
        Long userId,
        int capCount,
        double weightGrams,
        int pointsEarned,
        String qrToken,
        SessionStatus status,
        LocalDateTime expiresAt,
        LocalDateTime confirmedAt,
        String blockchainTxHash
) {
}

package com.sidru.sidru_api.rewards.interfaces.rest.resources;

import com.sidru.sidru_api.rewards.domain.model.valueobjects.TransactionType;

import java.time.LocalDateTime;

public record PointTransactionResource(
        Long id,
        Long userId,
        TransactionType type,
        int points,
        String description,
        String blockchainTxHash,
        LocalDateTime createdAt
) {
}

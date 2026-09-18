package com.sidru.sidru_api.blockchain.interfaces.rest.resources;

import java.time.LocalDateTime;

public record WithdrawalStatusResource(
        Long id,
        String mode,
        int points,
        String amountWei,
        String toAddress,
        String status,
        String txHash,
        String explorerUrl,
        String failureReason,
        String reserveOut,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}

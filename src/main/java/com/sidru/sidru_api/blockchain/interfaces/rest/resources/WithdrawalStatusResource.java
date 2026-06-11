package com.sidru.sidru_api.blockchain.interfaces.rest.resources;

public record WithdrawalStatusResource(
        Long id,
        String toAddress,
        String amountWei,
        String status,
        String txHash,
        String explorerUrl
) {}

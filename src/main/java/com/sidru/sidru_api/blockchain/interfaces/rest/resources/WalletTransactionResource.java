package com.sidru.sidru_api.blockchain.interfaces.rest.resources;

public record WalletTransactionResource(
        String type,
        String txHash,
        String status,
        String explorerUrl
) {}

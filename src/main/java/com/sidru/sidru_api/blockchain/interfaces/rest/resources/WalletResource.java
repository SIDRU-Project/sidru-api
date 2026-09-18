package com.sidru.sidru_api.blockchain.interfaces.rest.resources;

public record WalletResource(
        int pointsBalance,
        String ctcEquivalent,
        String solesEquivalent,
        String network,
        String explorerBaseUrl,
        String linkedWallet,
        int minWithdrawalPoints,
        boolean withdrawalsEnabled,
        boolean hasWithdrawalInProgress
) {}

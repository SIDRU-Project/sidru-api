package com.sidru.sidru_api.blockchain.interfaces.rest.resources;

public record WalletResource(
        String address,
        String network,
        String balanceCtc,
        String balanceWei,
        String solesRef,
        String linkedWallet
) {}

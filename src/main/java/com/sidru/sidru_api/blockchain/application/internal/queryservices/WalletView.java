package com.sidru.sidru_api.blockchain.application.internal.queryservices;

/**
 * Read model of a citizen's custodial wallet snapshot.
 *
 * @param address     custodial EVM address (checksummed)
 * @param network     blockchain network label (polygon-amoy)
 * @param balanceWei  on-chain balance in wei (as decimal string)
 * @param balanceCtc  balance formatted with 18 decimals
 * @param solesRef    referential soles equivalence (balanceCtc / points-per-sol), informational
 * @param linkedWallet last external wallet used for withdrawal, if any
 */
public record WalletView(
        String address,
        String network,
        String balanceWei,
        String balanceCtc,
        String solesRef,
        String linkedWallet
) {}

package com.sidru.sidru_api.blockchain.application.internal.queryservices;

/**
 * Read model of a single on-chain wallet transaction.
 *
 * @param type        MINT or WITHDRAW
 * @param txHash      transaction hash
 * @param status      pending/confirmed (MINT) or withdrawal status
 * @param explorerUrl Polygonscan link
 */
public record WalletTransactionView(
        String type,
        String txHash,
        String status,
        String explorerUrl
) {}

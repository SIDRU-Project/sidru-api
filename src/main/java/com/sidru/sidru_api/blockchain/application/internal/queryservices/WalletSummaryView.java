package com.sidru.sidru_api.blockchain.application.internal.queryservices;

/**
 * Read model of the citizen wallet summary (spec sidru-mainnet, api-contract.md GET /wallet/me).
 * Puntos son la unica fuente del saldo: no hay direccion custodial ni balance on-chain que leer.
 *
 * @param pointsBalance          UserProfile.totalPoints
 * @param ctcEquivalent          pointsBalance como string (1 punto = 1 CTC)
 * @param solesEquivalent        pointsBalance / 100, dos decimales
 * @param network                etiqueta de red (p. ej. "polygon")
 * @param explorerBaseUrl        base del explorador de bloques
 * @param linkedWallet           toAddress del ultimo retiro COMPLETADO, o null
 * @param minWithdrawalPoints    minimo de puntos para poder retirar
 * @param withdrawalsEnabled     sidru.withdrawal.enabled
 * @param hasWithdrawalInProgress true si el usuario tiene un retiro EN_PROCESO
 */
public record WalletSummaryView(
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

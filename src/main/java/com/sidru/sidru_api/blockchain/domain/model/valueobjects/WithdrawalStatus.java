package com.sidru.sidru_api.blockchain.domain.model.valueobjects;

/**
 * Lifecycle of a custodial withdrawal request (idempotency guard).
 */
public enum WithdrawalStatus {
    EN_PROCESO,
    COMPLETADO,
    FALLIDO
}

package com.sidru.sidru_api.blockchain.domain.model.valueobjects;

/**
 * Modo de retiro: CTC mintea directo a la wallet del ciudadano; USDC paga desde la
 * reserva a la par (para llegar a exchanges que no listan CTC, p. ej. Lemon).
 */
public enum WithdrawalMode {
    CTC,
    USDC
}

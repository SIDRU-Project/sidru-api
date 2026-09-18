package com.sidru.sidru_api.blockchain.domain.model.exceptions;

/** Thrown when the requested points are below sidru.withdrawal.min-points. */
public class BelowMinimumWithdrawalException extends RuntimeException {
    public BelowMinimumWithdrawalException(String message) {
        super(message);
    }
}

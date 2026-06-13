package com.sidru.sidru_api.blockchain.domain.model.exceptions;

/** Thrown when a withdrawal is already EN_PROCESO for the user (idempotency). */
public class WithdrawalInProgressException extends RuntimeException {
    public WithdrawalInProgressException(String message) {
        super(message);
    }
}

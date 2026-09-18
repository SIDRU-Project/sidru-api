package com.sidru.sidru_api.blockchain.domain.model.exceptions;

/** Thrown when sidru.withdrawal.enabled=false and a withdrawal is requested. */
public class WithdrawalsDisabledException extends RuntimeException {
    public WithdrawalsDisabledException(String message) {
        super(message);
    }
}

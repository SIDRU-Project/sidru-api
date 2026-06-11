package com.sidru.sidru_api.blockchain.domain.model.exceptions;

/** Thrown when the custodial balance is zero and a withdrawal is requested. */
public class NoBalanceToWithdrawException extends RuntimeException {
    public NoBalanceToWithdrawException(String message) {
        super(message);
    }
}

package com.sidru.sidru_api.blockchain.domain.model.exceptions;

/** Thrown when the requested withdrawal destination fails EIP-55 validation. */
public class InvalidWithdrawalAddressException extends RuntimeException {
    public InvalidWithdrawalAddressException(String message) {
        super(message);
    }
}

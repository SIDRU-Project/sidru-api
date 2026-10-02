package com.sidru.sidru_api.iam.domain.model.exceptions;

/** ID token inválido, vencido o de otra audiencia/emisor. */
public class InvalidSocialTokenException extends RuntimeException {
    public InvalidSocialTokenException() {
        super();
    }

    public InvalidSocialTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}

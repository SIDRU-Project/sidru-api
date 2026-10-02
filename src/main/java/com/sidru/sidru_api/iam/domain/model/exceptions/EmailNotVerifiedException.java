package com.sidru.sidru_api.iam.domain.model.exceptions;

/** El correo del proveedor no está verificado. */
public class EmailNotVerifiedException extends RuntimeException {
    public EmailNotVerifiedException() {
        super();
    }

    public EmailNotVerifiedException(String message, Throwable cause) {
        super(message, cause);
    }
}

package com.sidru.sidru_api.iam.domain.model.exceptions;

/** No se pudo obtener las claves del proveedor. */
public class SocialProviderUnavailableException extends RuntimeException {
    public SocialProviderUnavailableException() {
        super();
    }

    public SocialProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

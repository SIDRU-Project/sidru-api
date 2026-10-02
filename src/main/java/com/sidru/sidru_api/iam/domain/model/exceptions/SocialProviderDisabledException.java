package com.sidru.sidru_api.iam.domain.model.exceptions;

/** Proveedor no configurado. */
public class SocialProviderDisabledException extends RuntimeException {
    public SocialProviderDisabledException() {
        super();
    }

    public SocialProviderDisabledException(String message, Throwable cause) {
        super(message, cause);
    }
}

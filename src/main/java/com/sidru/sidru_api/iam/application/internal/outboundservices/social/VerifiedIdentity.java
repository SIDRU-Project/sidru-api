package com.sidru.sidru_api.iam.application.internal.outboundservices.social;

/**
 * Claims ya verificados de un ID token: lo único que SIDRU toma del proveedor sobre la identidad.
 * {@code nonce} es el claim del token (null si no lo trae); lo compara el servicio de aplicación.
 */
public record VerifiedIdentity(String subject, String email, boolean emailVerified, String name, String nonce) {

    public VerifiedIdentity(String subject, String email, boolean emailVerified, String name) {
        this(subject, email, emailVerified, name, null);
    }
}

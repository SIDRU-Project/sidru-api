package com.sidru.sidru_api.iam.application.internal.outboundservices.social;

/** Claims ya verificados de un ID token: lo único que SIDRU toma del proveedor sobre la identidad. */
public record VerifiedIdentity(String subject, String email, boolean emailVerified, String name) {
}

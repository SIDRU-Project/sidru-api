package com.sidru.sidru_api.iam.domain.model.commands;

import com.sidru.sidru_api.iam.domain.model.valueobjects.SocialProvider;

/**
 * Acceso con un proveedor externo. {@code token} es el ID token del proveedor; {@code fullName}
 * solo se usa para mostrar al crear la cuenta (Apple lo entrega aparte, solo la primera vez).
 * {@code nonce} es opcional: si viene, el ID token debe traer el mismo claim {@code nonce} (flujo web por
 * redirección, design 3.3); si es null, no se exige.
 */
public record SocialSignInCommand(SocialProvider provider, String token, String fullName, String nonce) {

    /** Sin nonce (Apple y el futuro flujo nativo). */
    public SocialSignInCommand(SocialProvider provider, String token, String fullName) {
        this(provider, token, fullName, null);
    }
}

package com.sidru.sidru_api.iam.domain.model.commands;

import com.sidru.sidru_api.iam.domain.model.valueobjects.SocialProvider;

/**
 * Acceso con un proveedor externo. {@code token} es el ID token del proveedor; {@code fullName}
 * solo se usa para mostrar al crear la cuenta (Apple lo entrega aparte, solo la primera vez).
 */
public record SocialSignInCommand(SocialProvider provider, String token, String fullName) {
}

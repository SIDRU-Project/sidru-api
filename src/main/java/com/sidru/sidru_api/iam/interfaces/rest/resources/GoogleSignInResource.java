package com.sidru.sidru_api.iam.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GoogleSignInResource(
        @NotBlank(message = "idToken es obligatorio")
        @Size(max = 4096, message = "idToken excede el tamaño máximo")
        String idToken,

        /** Opcional: si viene, el ID token debe traer el mismo claim nonce (flujo web por redirección). */
        @Size(max = 128, message = "nonce excede el tamaño máximo")
        String nonce
) {
}

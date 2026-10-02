package com.sidru.sidru_api.iam.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code fullName} es opcional: Apple lo entrega aparte y solo en el primer acceso. */
public record AppleSignInResource(
        @NotBlank(message = "identityToken es obligatorio")
        @Size(max = 4096, message = "identityToken excede el tamaño máximo")
        String identityToken,

        @Size(max = 200, message = "fullName excede 200 caracteres")
        String fullName
) {
}

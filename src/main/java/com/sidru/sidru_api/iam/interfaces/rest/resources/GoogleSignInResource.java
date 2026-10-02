package com.sidru.sidru_api.iam.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GoogleSignInResource(
        @NotBlank(message = "idToken es obligatorio")
        @Size(max = 4096, message = "idToken excede el tamaño máximo")
        String idToken
) {
}

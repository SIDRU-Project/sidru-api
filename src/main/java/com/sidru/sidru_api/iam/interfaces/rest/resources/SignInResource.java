package com.sidru.sidru_api.iam.interfaces.rest.resources;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SignInResource(
        @NotNull(message = "{user.email.not.null}")
        @NotBlank(message = "{user.email.not.blank}")
        @Email(message = "{user.email.invalid}")
        String email,

        @NotNull(message = "{user.password.not.null}")
        @NotBlank(message = "{user.password.not.blank}")
        @Size(min = 8, max = 30, message = "{user.password.size}")
        String password
) {
}

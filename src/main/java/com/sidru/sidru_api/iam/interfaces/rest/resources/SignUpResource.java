package com.sidru.sidru_api.iam.interfaces.rest.resources;

import jakarta.validation.constraints.*;

import java.util.List;

public record SignUpResource(
        @NotNull(message = "{user.fullName.not.null}")
        @NotBlank(message = "{user.fullName.not.blank}")
        @Size(max = 200, message = "{user.fullName.size}")
        String fullName,

        @NotNull(message = "{user.email.not.null}")
        @NotBlank(message = "{user.email.not.blank}")
        @Email(message = "{user.email.invalid}")
        String email,

        @NotNull(message = "{user.password.not.null}")
        @NotBlank(message = "{user.password.not.blank}")
        @Size(min = 8, max = 30, message = "{user.password.size}")
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$", message = "{user.password.pattern}")
        String password,

        @Size(max = 20, message = "{user.phone.size}")
        String phone,

        @Size(max = 200, message = "{user.district.size}")
        String district,

        List<String> roles
) {
}

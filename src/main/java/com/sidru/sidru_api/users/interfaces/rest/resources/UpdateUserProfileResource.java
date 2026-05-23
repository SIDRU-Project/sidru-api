package com.sidru.sidru_api.users.interfaces.rest.resources;

import jakarta.validation.constraints.Size;

public record UpdateUserProfileResource(
        @Size(max = 200) String fullName,
        @Size(max = 20) String phone,
        @Size(max = 200) String district
) {
}

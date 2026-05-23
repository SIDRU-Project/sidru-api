package com.sidru.sidru_api.sessions.interfaces.rest.resources;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record CreateRecyclingSessionResource(
        @NotNull @Min(1) Integer capCount,
        @PositiveOrZero double weightGrams
) {
}

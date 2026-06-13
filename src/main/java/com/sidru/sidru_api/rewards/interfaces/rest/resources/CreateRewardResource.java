package com.sidru.sidru_api.rewards.interfaces.rest.resources;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateRewardResource(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 500) String description,
        @Min(1) int pointsCost,
        @PositiveOrZero int stock,
        String imageUrl
) {
}

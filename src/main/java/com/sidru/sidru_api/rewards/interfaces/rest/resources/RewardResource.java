package com.sidru.sidru_api.rewards.interfaces.rest.resources;

public record RewardResource(
        Long id,
        String name,
        String description,
        int pointsCost,
        int stock,
        boolean active,
        String imageUrl
) {
}

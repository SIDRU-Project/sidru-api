package com.sidru.sidru_api.users.interfaces.rest.resources;

public record UserProfileResource(
        Long id,
        Long userId,
        String fullName,
        String phone,
        String district,
        int totalPoints,
        int totalCaps,
        int totalSessions
) {
}

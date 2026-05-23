package com.sidru.sidru_api.users.domain.model.commands;

public record CreateUserProfileCommand(
        Long userId,
        String fullName,
        String phone,
        String district
) {
}

package com.sidru.sidru_api.users.infrastructure.utils;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum UsersErrorCatalog {
    USER_PROFILE_NOT_FOUND("ERR_USERS_001", "User profile not found"),
    INSUFFICIENT_POINTS("ERR_USERS_002", "Insufficient points"),
    GENERIC_ERROR("ERR_USERS_999", "An unexpected error occurred"),
    INVALID_PARAMETER("ERR_INVALID_001", "Invalid parameter"),
    INVALID_JSON("ERR_JSON_001", "Invalid JSON");

    private final String code;
    private final String message;
}

package com.sidru.sidru_api.iam.infrastructure.utils;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum IamErrorCatalog {
    USER_NOT_FOUND("ERR_IAM_001", "User not found"),
    INVALID_CREDENTIALS("ERR_IAM_002", "Invalid credentials"),
    USERNAME_ALREADY_EXISTS("ERR_IAM_003", "Username already exists"),
    EMAIL_ALREADY_EXISTS("ERR_IAM_004", "Email already exists"),
    ROLE_NOT_FOUND("ERR_IAM_005", "Role not found"),
    GENERIC_ERROR("ERR_IAM_999", "An unexpected error occurred"),
    INVALID_PARAMETER("ERR_INVALID_001", "Invalid parameter"),
    INVALID_JSON("ERR_JSON_001", "Invalid JSON");

    private final String code;
    private final String message;
}

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
    SOCIAL_INVALID_TOKEN("ERR_AUTH_SOCIAL_INVALID_TOKEN", "Invalid social identity token"),
    SOCIAL_ADMIN_NOT_ALLOWED("ERR_AUTH_SOCIAL_ADMIN", "Admin accounts cannot sign in with a social provider"),
    EMAIL_NOT_VERIFIED("ERR_AUTH_EMAIL_NOT_VERIFIED", "The provider email is not verified"),
    SOCIAL_PROVIDER_DISABLED("ERR_AUTH_PROVIDER_DISABLED", "Social provider is not enabled"),
    SOCIAL_PROVIDER_UNAVAILABLE("ERR_AUTH_PROVIDER_UNAVAILABLE", "Social provider keys are temporarily unavailable"),
    GENERIC_ERROR("ERR_IAM_999", "An unexpected error occurred"),
    INVALID_PARAMETER("ERR_INVALID_001", "Invalid parameter"),
    INVALID_JSON("ERR_JSON_001", "Invalid JSON");

    private final String code;
    private final String message;
}

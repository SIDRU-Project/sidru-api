package com.sidru.sidru_api.sessions.infrastructure.utils;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SessionsErrorCatalog {
    SESSION_NOT_FOUND("ERR_SES_001", "Recycling session not found"),
    INVALID_SESSION_STATE("ERR_SES_002", "Session is not in a valid state for this operation"),
    SESSION_EXPIRED("ERR_SES_003", "Session has expired"),
    INVALID_CAP_COUNT("ERR_SES_004", "Cap count is outside the allowed range"),
    UNAUTHORIZED_DEVICE("ERR_SES_005", "Smart Bin API key is invalid"),
    INVALID_SESSION_WEIGHT("ERR_SES_006", "Invalid session weight"),
    GENERIC_ERROR("ERR_SES_999", "An unexpected error occurred");

    private final String code;
    private final String message;
}

package com.sidru.sidru_api.devices.infrastructure.utils;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum DevicesErrorCatalog {
    SMART_BIN_NOT_FOUND("ERR_DEV_001", "Smart bin not found"),
    DEVICE_CODE_ALREADY_EXISTS("ERR_DEV_002", "Device code already exists"),
    GENERIC_ERROR("ERR_DEV_999", "An unexpected error occurred");

    private final String code;
    private final String message;
}

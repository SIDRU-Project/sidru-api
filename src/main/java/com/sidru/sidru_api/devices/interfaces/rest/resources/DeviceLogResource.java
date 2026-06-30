package com.sidru.sidru_api.devices.interfaces.rest.resources;

import java.time.LocalDateTime;

public record DeviceLogResource(
        Long id,
        String deviceCode,
        String type,
        String detail,
        String rawPayload,
        LocalDateTime receivedAt) {
}

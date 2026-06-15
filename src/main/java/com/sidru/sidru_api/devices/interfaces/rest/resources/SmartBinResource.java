package com.sidru.sidru_api.devices.interfaces.rest.resources;

import com.sidru.sidru_api.devices.domain.model.valueobjects.BinStatus;

public record SmartBinResource(
        Long id,
        String deviceCode,
        String location,
        String district,
        BinStatus status,
        int totalCapsCollected
) {
}

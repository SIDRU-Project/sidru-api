package com.sidru.sidru_api.devices.interfaces.rest.resources;

import com.sidru.sidru_api.devices.domain.model.valueobjects.BinStatus;

public record UpdateSmartBinResource(String location, String district, BinStatus status) {
}

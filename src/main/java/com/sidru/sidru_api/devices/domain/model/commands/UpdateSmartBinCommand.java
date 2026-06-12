package com.sidru.sidru_api.devices.domain.model.commands;

import com.sidru.sidru_api.devices.domain.model.valueobjects.BinStatus;

public record UpdateSmartBinCommand(Long id, String location, String district, BinStatus status) {
}

package com.sidru.sidru_api.devices.domain.model.commands;

public record RegisterSmartBinCommand(String deviceCode, String location, String district) {
}

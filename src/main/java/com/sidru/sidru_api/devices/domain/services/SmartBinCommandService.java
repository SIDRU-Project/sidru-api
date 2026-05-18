package com.sidru.sidru_api.devices.domain.services;

import com.sidru.sidru_api.devices.domain.model.aggregates.SmartBin;
import com.sidru.sidru_api.devices.domain.model.commands.AddCapsToSmartBinCommand;
import com.sidru.sidru_api.devices.domain.model.commands.RegisterSmartBinCommand;
import com.sidru.sidru_api.devices.domain.model.commands.UpdateSmartBinCommand;

import java.util.Optional;

public interface SmartBinCommandService {
    Optional<SmartBin> handle(RegisterSmartBinCommand command);
    Optional<SmartBin> handle(UpdateSmartBinCommand command);
    Optional<SmartBin> handle(AddCapsToSmartBinCommand command);
}

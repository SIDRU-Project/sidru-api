package com.sidru.sidru_api.devices.interfaces.rest.transform;

import com.sidru.sidru_api.devices.domain.model.commands.RegisterSmartBinCommand;
import com.sidru.sidru_api.devices.interfaces.rest.resources.RegisterSmartBinResource;

public class RegisterSmartBinCommandFromResourceAssembler {

    public static RegisterSmartBinCommand toCommandFromResource(RegisterSmartBinResource resource) {
        return new RegisterSmartBinCommand(resource.deviceCode(), resource.location(), resource.district());
    }
}

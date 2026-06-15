package com.sidru.sidru_api.devices.interfaces.rest.transform;

import com.sidru.sidru_api.devices.domain.model.commands.UpdateSmartBinCommand;
import com.sidru.sidru_api.devices.interfaces.rest.resources.UpdateSmartBinResource;

public class UpdateSmartBinCommandFromResourceAssembler {

    public static UpdateSmartBinCommand toCommandFromResource(Long id, UpdateSmartBinResource resource) {
        return new UpdateSmartBinCommand(id, resource.location(), resource.district(), resource.status());
    }
}

package com.sidru.sidru_api.devices.interfaces.rest.transform;

import com.sidru.sidru_api.devices.domain.model.aggregates.SmartBin;
import com.sidru.sidru_api.devices.interfaces.rest.resources.SmartBinResource;

public class SmartBinResourceFromEntityAssembler {

    public static SmartBinResource toResourceFromEntity(SmartBin bin) {
        return new SmartBinResource(
                bin.getId(),
                bin.getDeviceCode(),
                bin.getLocation(),
                bin.getDistrict(),
                bin.getStatus(),
                bin.getTotalCapsCollected()
        );
    }
}

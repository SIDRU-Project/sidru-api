package com.sidru.sidru_api.sessions.application.internal.outboundservices.acl;

import com.sidru.sidru_api.devices.interfaces.acl.DevicesContextFacade;
import org.springframework.stereotype.Service;

@Service("sessionsExternalDevicesService")
public class ExternalDevicesService {

    private final DevicesContextFacade devicesContextFacade;

    public ExternalDevicesService(DevicesContextFacade devicesContextFacade) {
        this.devicesContextFacade = devicesContextFacade;
    }

    public Long resolveSmartBinIdByApiKey(String apiKey) {
        return devicesContextFacade.fetchSmartBinIdByApiKey(apiKey);
    }

    public Long addCaps(Long smartBinId, int caps) {
        return devicesContextFacade.addCaps(smartBinId, caps);
    }
}

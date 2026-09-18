package com.sidru.sidru_api.devices.application.acl;

import com.sidru.sidru_api.devices.application.internal.commandservices.DeviceLogService;
import com.sidru.sidru_api.devices.domain.model.commands.AddCapsToSmartBinCommand;
import com.sidru.sidru_api.devices.domain.model.queries.GetSmartBinByApiKeyQuery;
import com.sidru.sidru_api.devices.domain.model.queries.GetSmartBinByIdQuery;
import com.sidru.sidru_api.devices.domain.services.SmartBinCommandService;
import com.sidru.sidru_api.devices.domain.services.SmartBinQueryService;
import com.sidru.sidru_api.devices.infrastructure.persistence.jpa.repositories.SmartBinRepository;
import com.sidru.sidru_api.devices.interfaces.acl.DevicesContextFacade;
import org.springframework.stereotype.Service;

@Service
public class DevicesContextFacadeImpl implements DevicesContextFacade {

    private final SmartBinQueryService smartBinQueryService;
    private final SmartBinCommandService smartBinCommandService;
    private final DeviceLogService deviceLogService;
    private final SmartBinRepository smartBinRepository;

    public DevicesContextFacadeImpl(SmartBinQueryService smartBinQueryService,
                                    SmartBinCommandService smartBinCommandService,
                                    DeviceLogService deviceLogService,
                                    SmartBinRepository smartBinRepository) {
        this.smartBinQueryService = smartBinQueryService;
        this.smartBinCommandService = smartBinCommandService;
        this.deviceLogService = deviceLogService;
        this.smartBinRepository = smartBinRepository;
    }

    @Override
    public Long fetchSmartBinIdByApiKey(String apiKey) {
        var bin = smartBinQueryService.handle(new GetSmartBinByApiKeyQuery(apiKey));
        return bin.map(b -> b.getId()).orElse(0L);
    }

    @Override
    public Boolean existsById(Long smartBinId) {
        return smartBinQueryService.handle(new GetSmartBinByIdQuery(smartBinId)).isPresent();
    }

    @Override
    public Long addCaps(Long smartBinId, int caps) {
        var bin = smartBinCommandService.handle(new AddCapsToSmartBinCommand(smartBinId, caps));
        return bin.map(b -> b.getId()).orElse(0L);
    }

    @Override
    public String fetchDeviceCodeById(Long smartBinId) {
        return smartBinQueryService.handle(new GetSmartBinByIdQuery(smartBinId))
                .map(b -> b.getDeviceCode())
                .orElse(null);
    }

    @Override
    public void recordDeviceLog(String deviceCode, String type, String detail, String rawPayload) {
        deviceLogService.record(deviceCode, type, detail, rawPayload);
    }

    @Override
    public long countBins() {
        return smartBinRepository.count();
    }
}

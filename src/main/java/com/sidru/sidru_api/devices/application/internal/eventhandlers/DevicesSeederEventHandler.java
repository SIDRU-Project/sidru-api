package com.sidru.sidru_api.devices.application.internal.eventhandlers;

import com.sidru.sidru_api.devices.domain.model.commands.RegisterSmartBinCommand;
import com.sidru.sidru_api.devices.domain.services.SmartBinCommandService;
import com.sidru.sidru_api.devices.infrastructure.persistence.jpa.repositories.SmartBinRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

@Service
public class DevicesSeederEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(DevicesSeederEventHandler.class);

    private final SmartBinCommandService smartBinCommandService;
    private final SmartBinRepository smartBinRepository;

    public DevicesSeederEventHandler(SmartBinCommandService smartBinCommandService,
                                     SmartBinRepository smartBinRepository) {
        this.smartBinCommandService = smartBinCommandService;
        this.smartBinRepository = smartBinRepository;
    }

    @EventListener
    @Order(20)
    public void on(ApplicationReadyEvent event) {
        if (smartBinRepository.existsByDeviceCode("BIN-001")) return;

        var command = new RegisterSmartBinCommand(
                "BIN-001",
                "Av. Universitaria 1800, San Miguel",
                "San Miguel"
        );
        var bin = smartBinCommandService.handle(command);
        bin.ifPresent(b ->
                LOGGER.info("Demo SmartBin seeded — deviceCode: BIN-001 / apiKey: {}", b.getApiKey()));
    }
}

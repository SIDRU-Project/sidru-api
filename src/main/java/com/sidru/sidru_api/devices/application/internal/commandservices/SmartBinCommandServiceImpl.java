package com.sidru.sidru_api.devices.application.internal.commandservices;

import com.sidru.sidru_api.devices.domain.model.aggregates.SmartBin;
import com.sidru.sidru_api.devices.domain.model.commands.AddCapsToSmartBinCommand;
import com.sidru.sidru_api.devices.domain.model.commands.RegisterSmartBinCommand;
import com.sidru.sidru_api.devices.domain.model.commands.UpdateSmartBinCommand;
import com.sidru.sidru_api.devices.domain.model.exceptions.DeviceCodeAlreadyExistsException;
import com.sidru.sidru_api.devices.domain.model.exceptions.SmartBinNotFoundException;
import com.sidru.sidru_api.devices.domain.services.SmartBinCommandService;
import com.sidru.sidru_api.devices.infrastructure.persistence.jpa.repositories.SmartBinRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class SmartBinCommandServiceImpl implements SmartBinCommandService {

    private final SmartBinRepository smartBinRepository;

    public SmartBinCommandServiceImpl(SmartBinRepository smartBinRepository) {
        this.smartBinRepository = smartBinRepository;
    }

    @Override
    @Transactional
    public Optional<SmartBin> handle(RegisterSmartBinCommand command) {
        if (smartBinRepository.existsByDeviceCode(command.deviceCode())) {
            throw new DeviceCodeAlreadyExistsException();
        }
        var bin = new SmartBin(command.deviceCode(), command.location(), command.district());
        return Optional.of(smartBinRepository.save(bin));
    }

    @Override
    @Transactional
    public Optional<SmartBin> handle(UpdateSmartBinCommand command) {
        var bin = smartBinRepository.findById(command.id())
                .orElseThrow(SmartBinNotFoundException::new);
        if (command.location() != null) bin.setLocation(command.location());
        if (command.district() != null) bin.setDistrict(command.district());
        if (command.status() != null) bin.setStatus(command.status());
        return Optional.of(smartBinRepository.save(bin));
    }

    @Override
    @Transactional
    public Optional<SmartBin> handle(AddCapsToSmartBinCommand command) {
        var bin = smartBinRepository.findById(command.smartBinId())
                .orElseThrow(SmartBinNotFoundException::new);
        bin.addCaps(command.caps());
        return Optional.of(smartBinRepository.save(bin));
    }
}

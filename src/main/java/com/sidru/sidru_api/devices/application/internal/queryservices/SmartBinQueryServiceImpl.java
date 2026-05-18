package com.sidru.sidru_api.devices.application.internal.queryservices;

import com.sidru.sidru_api.devices.domain.model.aggregates.SmartBin;
import com.sidru.sidru_api.devices.domain.model.queries.GetAllSmartBinsQuery;
import com.sidru.sidru_api.devices.domain.model.queries.GetSmartBinByApiKeyQuery;
import com.sidru.sidru_api.devices.domain.model.queries.GetSmartBinByIdQuery;
import com.sidru.sidru_api.devices.domain.services.SmartBinQueryService;
import com.sidru.sidru_api.devices.infrastructure.persistence.jpa.repositories.SmartBinRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class SmartBinQueryServiceImpl implements SmartBinQueryService {

    private final SmartBinRepository smartBinRepository;

    public SmartBinQueryServiceImpl(SmartBinRepository smartBinRepository) {
        this.smartBinRepository = smartBinRepository;
    }

    @Override
    public List<SmartBin> handle(GetAllSmartBinsQuery query) {
        return smartBinRepository.findAll();
    }

    @Override
    public Optional<SmartBin> handle(GetSmartBinByIdQuery query) {
        return smartBinRepository.findById(query.id());
    }

    @Override
    public Optional<SmartBin> handle(GetSmartBinByApiKeyQuery query) {
        return smartBinRepository.findByApiKey(query.apiKey());
    }
}

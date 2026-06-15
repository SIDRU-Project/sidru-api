package com.sidru.sidru_api.devices.domain.services;

import com.sidru.sidru_api.devices.domain.model.aggregates.SmartBin;
import com.sidru.sidru_api.devices.domain.model.queries.GetAllSmartBinsQuery;
import com.sidru.sidru_api.devices.domain.model.queries.GetSmartBinByApiKeyQuery;
import com.sidru.sidru_api.devices.domain.model.queries.GetSmartBinByIdQuery;

import java.util.List;
import java.util.Optional;

public interface SmartBinQueryService {
    List<SmartBin> handle(GetAllSmartBinsQuery query);
    Optional<SmartBin> handle(GetSmartBinByIdQuery query);
    Optional<SmartBin> handle(GetSmartBinByApiKeyQuery query);
}

package com.sidru.sidru_api.devices.application.internal.commandservices;

import com.sidru.sidru_api.devices.domain.model.aggregates.DeviceLog;
import com.sidru.sidru_api.devices.infrastructure.persistence.jpa.repositories.DeviceLogRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Registro y consulta de logs de dispositivos (US-30). Telemetría de diagnóstico:
 * el inbound MQTT lo alimenta y el endpoint de admin lo lista. No es flujo de negocio,
 * por eso se mantiene como un servicio simple (sin CQRS).
 */
@Service
public class DeviceLogService {

    private final DeviceLogRepository repository;

    public DeviceLogService(DeviceLogRepository repository) {
        this.repository = repository;
    }

    public void record(String deviceCode, String type, String detail, String rawPayload) {
        repository.save(new DeviceLog(deviceCode, type, detail, rawPayload));
    }

    public List<DeviceLog> recent() {
        return repository.findTop100ByOrderByIdDesc();
    }

    public List<DeviceLog> recent(String deviceCode) {
        return repository.findTop100ByDeviceCodeOrderByIdDesc(deviceCode);
    }
}

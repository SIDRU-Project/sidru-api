package com.sidru.sidru_api.devices.infrastructure.persistence.jpa.repositories;

import com.sidru.sidru_api.devices.domain.model.aggregates.DeviceLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeviceLogRepository extends JpaRepository<DeviceLog, Long> {
    List<DeviceLog> findTop100ByOrderByIdDesc();
    List<DeviceLog> findTop100ByDeviceCodeOrderByIdDesc(String deviceCode);
}

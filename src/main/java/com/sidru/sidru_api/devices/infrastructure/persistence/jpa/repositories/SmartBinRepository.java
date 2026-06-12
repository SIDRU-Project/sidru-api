package com.sidru.sidru_api.devices.infrastructure.persistence.jpa.repositories;

import com.sidru.sidru_api.devices.domain.model.aggregates.SmartBin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SmartBinRepository extends JpaRepository<SmartBin, Long> {
    Optional<SmartBin> findByDeviceCode(String deviceCode);
    Optional<SmartBin> findByApiKey(String apiKey);
    boolean existsByDeviceCode(String deviceCode);
}

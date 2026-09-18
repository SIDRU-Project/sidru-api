package com.sidru.sidru_api.devices.interfaces.acl;

public interface DevicesContextFacade {

    /** Returns the SmartBin id authenticated by the given API key, or 0L if not found. */
    Long fetchSmartBinIdByApiKey(String apiKey);

    Boolean existsById(Long smartBinId);

    /** Registers caps after a successful recycling session. */
    Long addCaps(Long smartBinId, int caps);

    /** Returns the deviceCode (e.g. "BIN-001") of the Smart Bin, or {@code null} if not found. */
    String fetchDeviceCodeById(Long smartBinId);

    /** Persists a device telemetry/log entry received over MQTT (US-30). */
    void recordDeviceLog(String deviceCode, String type, String detail, String rawPayload);

    /** Total de Smart Bins registrados (métricas, US-36). */
    long countBins();
}

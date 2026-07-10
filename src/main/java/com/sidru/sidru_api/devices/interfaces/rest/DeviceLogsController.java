package com.sidru.sidru_api.devices.interfaces.rest;

import com.sidru.sidru_api.devices.application.internal.commandservices.DeviceLogService;
import com.sidru.sidru_api.devices.interfaces.rest.resources.DeviceLogResource;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Consulta de logs/telemetría de los Smart Bins (US-30), solo para administradores.
 * Los alimenta el listener MQTT inbound del contexto mqtt.
 */
@RestController
@RequestMapping(value = "/device-logs", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Device Logs", description = "Telemetría/diagnóstico enviada por los Smart Bins")
public class DeviceLogsController {

    private final DeviceLogService deviceLogService;

    public DeviceLogsController(DeviceLogService deviceLogService) {
        this.deviceLogService = deviceLogService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<List<DeviceLogResource>> getRecent(
            @RequestParam(required = false) String deviceCode) {
        var logs = (deviceCode == null || deviceCode.isBlank())
                ? deviceLogService.recent()
                : deviceLogService.recent(deviceCode);
        return ResponseEntity.ok(logs.stream()
                .map(l -> new DeviceLogResource(
                        l.getId(), l.getDeviceCode(), l.getType(),
                        l.getDetail(), l.getRawPayload(), l.getCreatedAt()))
                .toList());
    }
}

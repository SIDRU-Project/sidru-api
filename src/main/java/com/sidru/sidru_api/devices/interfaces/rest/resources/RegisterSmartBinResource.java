package com.sidru.sidru_api.devices.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterSmartBinResource(
        @NotBlank @Size(max = 100) String deviceCode,
        @NotBlank @Size(max = 200) String location,
        @Size(max = 50) String district
) {
}

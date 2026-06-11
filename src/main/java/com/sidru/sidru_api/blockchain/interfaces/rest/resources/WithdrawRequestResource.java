package com.sidru.sidru_api.blockchain.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;

public record WithdrawRequestResource(
        @NotBlank String toAddress
) {}

package com.sidru.sidru_api.blockchain.interfaces.rest.resources;

import com.sidru.sidru_api.blockchain.domain.model.valueobjects.WithdrawalMode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record WithdrawRequestResource(
        @NotBlank String toAddress,
        @NotNull @Min(1) Integer points,
        @NotNull WithdrawalMode mode
) {}

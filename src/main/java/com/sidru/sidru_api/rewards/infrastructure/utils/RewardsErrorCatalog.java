package com.sidru.sidru_api.rewards.infrastructure.utils;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RewardsErrorCatalog {
    REWARD_NOT_FOUND("ERR_REW_001", "Reward not found"),
    REWARD_OUT_OF_STOCK("ERR_REW_002", "Reward is out of stock or inactive"),
    INSUFFICIENT_POINTS("ERR_REW_003", "User has insufficient points to redeem this reward"),
    GENERIC_ERROR("ERR_REW_999", "An unexpected error occurred");

    private final String code;
    private final String message;
}

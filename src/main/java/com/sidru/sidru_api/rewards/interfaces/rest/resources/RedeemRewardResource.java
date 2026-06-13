package com.sidru.sidru_api.rewards.interfaces.rest.resources;

import jakarta.validation.constraints.NotNull;

public record RedeemRewardResource(@NotNull Long rewardId) {
}

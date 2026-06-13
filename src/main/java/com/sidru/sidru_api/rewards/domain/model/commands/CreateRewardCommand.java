package com.sidru.sidru_api.rewards.domain.model.commands;

public record CreateRewardCommand(String name, String description, int pointsCost, int stock, String imageUrl) {
}

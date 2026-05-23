package com.sidru.sidru_api.sessions.domain.model.commands;

public record CreateRecyclingSessionCommand(String deviceApiKey, int capCount, double weightGrams) {
}

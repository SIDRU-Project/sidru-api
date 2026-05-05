package com.sidru.sidru_api.users.domain.model.commands;

public record AddPointsCommand(Long userId, int points, int caps) {
}

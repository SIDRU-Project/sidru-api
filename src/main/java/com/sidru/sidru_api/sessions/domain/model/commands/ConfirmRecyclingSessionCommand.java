package com.sidru.sidru_api.sessions.domain.model.commands;

public record ConfirmRecyclingSessionCommand(String qrToken, Long userId) {
}

package com.sidru.sidru_api.sessions.domain.services;

import com.sidru.sidru_api.sessions.domain.model.aggregates.RecyclingSession;
import com.sidru.sidru_api.sessions.domain.model.commands.CancelRecyclingSessionCommand;
import com.sidru.sidru_api.sessions.domain.model.commands.ConfirmRecyclingSessionCommand;
import com.sidru.sidru_api.sessions.domain.model.commands.CreateRecyclingSessionCommand;

import java.util.Optional;

public interface RecyclingSessionCommandService {
    Optional<RecyclingSession> handle(CreateRecyclingSessionCommand command);
    Optional<RecyclingSession> handle(ConfirmRecyclingSessionCommand command);
    Optional<RecyclingSession> handle(CancelRecyclingSessionCommand command);
}

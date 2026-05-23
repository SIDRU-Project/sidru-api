package com.sidru.sidru_api.sessions.domain.services;

import com.sidru.sidru_api.sessions.domain.model.aggregates.RecyclingSession;
import com.sidru.sidru_api.sessions.domain.model.queries.GetRecyclingSessionByIdQuery;
import com.sidru.sidru_api.sessions.domain.model.queries.GetRecyclingSessionByQrTokenQuery;
import com.sidru.sidru_api.sessions.domain.model.queries.GetRecyclingSessionsByUserIdQuery;

import java.util.List;
import java.util.Optional;

public interface RecyclingSessionQueryService {
    Optional<RecyclingSession> handle(GetRecyclingSessionByIdQuery query);
    Optional<RecyclingSession> handle(GetRecyclingSessionByQrTokenQuery query);
    List<RecyclingSession> handle(GetRecyclingSessionsByUserIdQuery query);
}

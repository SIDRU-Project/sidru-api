package com.sidru.sidru_api.sessions.application.internal.queryservices;

import com.sidru.sidru_api.sessions.domain.model.aggregates.RecyclingSession;
import com.sidru.sidru_api.sessions.domain.model.queries.GetRecyclingSessionByIdQuery;
import com.sidru.sidru_api.sessions.domain.model.queries.GetRecyclingSessionByQrTokenQuery;
import com.sidru.sidru_api.sessions.domain.model.queries.GetRecyclingSessionsByUserIdQuery;
import com.sidru.sidru_api.sessions.domain.services.RecyclingSessionQueryService;
import com.sidru.sidru_api.sessions.infrastructure.persistence.jpa.repositories.RecyclingSessionRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class RecyclingSessionQueryServiceImpl implements RecyclingSessionQueryService {

    private final RecyclingSessionRepository sessionRepository;

    public RecyclingSessionQueryServiceImpl(RecyclingSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Override
    public Optional<RecyclingSession> handle(GetRecyclingSessionByIdQuery query) {
        return sessionRepository.findById(query.id());
    }

    @Override
    public Optional<RecyclingSession> handle(GetRecyclingSessionByQrTokenQuery query) {
        return sessionRepository.findByQrToken(query.qrToken());
    }

    @Override
    public List<RecyclingSession> handle(GetRecyclingSessionsByUserIdQuery query) {
        return sessionRepository.findByUserIdOrderByCreatedAtDesc(query.userId());
    }
}

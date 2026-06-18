package com.sidru.sidru_api.sessions.application.acl;

import com.sidru.sidru_api.sessions.domain.model.valueobjects.SessionStatus;
import com.sidru.sidru_api.sessions.infrastructure.persistence.jpa.repositories.RecyclingSessionRepository;
import com.sidru.sidru_api.sessions.interfaces.acl.SessionsContextFacade;
import org.springframework.stereotype.Service;

@Service
public class SessionsContextFacadeImpl implements SessionsContextFacade {

    private final RecyclingSessionRepository sessionRepository;

    public SessionsContextFacadeImpl(RecyclingSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Override
    public long countAllSessions() {
        return sessionRepository.count();
    }

    @Override
    public long countConfirmedSessions() {
        return sessionRepository.countByStatus(SessionStatus.CONFIRMED);
    }

    @Override
    public long sumConfirmedCaps() {
        return sessionRepository.sumCapCountByStatus(SessionStatus.CONFIRMED);
    }

    @Override
    public double sumConfirmedWeightGrams() {
        return sessionRepository.sumWeightGramsByStatus(SessionStatus.CONFIRMED);
    }

    @Override
    public long sumConfirmedPoints() {
        return sessionRepository.sumPointsEarnedByStatus(SessionStatus.CONFIRMED);
    }
}

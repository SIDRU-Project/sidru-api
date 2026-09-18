package com.sidru.sidru_api.sessions.application.acl;

import com.sidru.sidru_api.sessions.domain.model.valueobjects.BinActivity;
import com.sidru.sidru_api.sessions.domain.model.valueobjects.SessionStatus;
import com.sidru.sidru_api.sessions.infrastructure.persistence.jpa.repositories.RecyclingSessionRepository;
import com.sidru.sidru_api.sessions.interfaces.acl.SessionsContextFacade;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

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

    // --- Variantes por rango de fechas (CP044 / US-36) ---

    @Override
    public long countAllSessions(LocalDateTime from, LocalDateTime to) {
        return sessionRepository.countAllInRange(from, to);
    }

    @Override
    public long countConfirmedSessions(LocalDateTime from, LocalDateTime to) {
        return sessionRepository.countByStatusInRange(SessionStatus.CONFIRMED, from, to);
    }

    @Override
    public long sumConfirmedCaps(LocalDateTime from, LocalDateTime to) {
        return sessionRepository.sumCapCountByStatusInRange(SessionStatus.CONFIRMED, from, to);
    }

    @Override
    public double sumConfirmedWeightGrams(LocalDateTime from, LocalDateTime to) {
        return sessionRepository.sumWeightGramsByStatusInRange(SessionStatus.CONFIRMED, from, to);
    }

    @Override
    public long sumConfirmedPoints(LocalDateTime from, LocalDateTime to) {
        return sessionRepository.sumPointsEarnedByStatusInRange(SessionStatus.CONFIRMED, from, to);
    }

    @Override
    public long countActiveUsers(LocalDateTime from, LocalDateTime to) {
        return sessionRepository.countDistinctUsersByStatusInRange(SessionStatus.CONFIRMED, from, to);
    }

    @Override
    public List<BinActivity> rankBins(LocalDateTime from, LocalDateTime to, int limit) {
        return sessionRepository.rankBinsByStatusInRange(
                SessionStatus.CONFIRMED, from, to, PageRequest.of(0, Math.max(1, limit)));
    }
}

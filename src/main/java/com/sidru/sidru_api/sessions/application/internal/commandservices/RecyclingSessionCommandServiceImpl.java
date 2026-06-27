package com.sidru.sidru_api.sessions.application.internal.commandservices;

import com.sidru.sidru_api.sessions.application.internal.outboundservices.acl.ExternalDevicesService;
import com.sidru.sidru_api.sessions.application.internal.outboundservices.acl.ExternalUserProfileService;
import com.sidru.sidru_api.sessions.application.internal.outboundservices.blockchain.BlockchainPort;
import com.sidru.sidru_api.sessions.domain.model.aggregates.RecyclingSession;
import com.sidru.sidru_api.sessions.domain.model.commands.CancelRecyclingSessionCommand;
import com.sidru.sidru_api.sessions.domain.model.commands.ConfirmRecyclingSessionCommand;
import com.sidru.sidru_api.sessions.domain.model.commands.CreateRecyclingSessionCommand;
import com.sidru.sidru_api.sessions.domain.model.exceptions.*;
import com.sidru.sidru_api.sessions.domain.services.RecyclingSessionCommandService;
import com.sidru.sidru_api.sessions.infrastructure.persistence.jpa.repositories.RecyclingSessionRepository;
import com.sidru.sidru_api.shared.domain.model.events.SessionConfirmedEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class RecyclingSessionCommandServiceImpl implements RecyclingSessionCommandService {

    private final RecyclingSessionRepository sessionRepository;
    private final ExternalDevicesService externalDevicesService;
    private final ExternalUserProfileService externalUserProfileService;
    private final BlockchainPort blockchainPort;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${sidru.recycling.price-per-kg-soles}")
    private double pricePerKgSoles;

    @Value("${sidru.recycling.points-per-sol}")
    private int pointsPerSol;

    @Value("${sidru.recycling.min-weight-grams}")
    private double minWeightGrams;

    @Value("${sidru.recycling.max-weight-grams-per-session}")
    private double maxWeightGrams;

    @Value("${sidru.recycling.session-expiry-minutes}")
    private int sessionExpiryMinutes;

    @Value("${sidru.recycling.min-caps}")
    private int minCaps;

    @Value("${sidru.recycling.max-caps-per-session}")
    private int maxCaps;

    public RecyclingSessionCommandServiceImpl(RecyclingSessionRepository sessionRepository,
                                              ExternalDevicesService externalDevicesService,
                                              ExternalUserProfileService externalUserProfileService,
                                              BlockchainPort blockchainPort,
                                              ApplicationEventPublisher eventPublisher) {
        this.sessionRepository = sessionRepository;
        this.externalDevicesService = externalDevicesService;
        this.externalUserProfileService = externalUserProfileService;
        this.blockchainPort = blockchainPort;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public Optional<RecyclingSession> handle(CreateRecyclingSessionCommand command) {
        Long smartBinId = externalDevicesService.resolveSmartBinIdByApiKey(command.deviceApiKey());
        if (smartBinId == 0L) {
            throw new UnauthorizedDeviceException();
        }
        if (command.capCount() < minCaps || command.capCount() > maxCaps) {
            throw new InvalidCapCountException();
        }
        validateWeight(command.weightGrams());
        int points = calculatePointsFromWeight(command.weightGrams());
        var session = new RecyclingSession(
                smartBinId,
                command.capCount(),
                command.weightGrams(),
                points,
                LocalDateTime.now().plusMinutes(sessionExpiryMinutes));
        return Optional.of(sessionRepository.save(session));
    }

    private int calculatePointsFromWeight(double weightGrams) {
        double weightKg = weightGrams / 1000.0;
        double estimatedValueSoles = weightKg * pricePerKgSoles;
        return (int) Math.round(estimatedValueSoles * pointsPerSol);
    }

    private void validateWeight(double weightGrams) {
        if (weightGrams < minWeightGrams || weightGrams > maxWeightGrams) {
            throw new InvalidSessionWeightException();
        }
    }

    @Override
    @Transactional
    public Optional<RecyclingSession> handle(ConfirmRecyclingSessionCommand command) {
        // Lock pesimista (US-23): serializa confirmaciones concurrentes del mismo QR.
        // La 2ª solicitud espera el commit de la 1ª, lee la sesión ya CONFIRMED y cae
        // en el guard de estado de abajo, evitando doble acreditación y doble mint.
        var session = sessionRepository.findByQrTokenForUpdate(command.qrToken())
                .orElseThrow(RecyclingSessionNotFoundException::new);

        if (!session.isPending()) {
            throw new InvalidSessionStateException();
        }
        if (session.isExpired()) {
            session.expire();
            sessionRepository.save(session);
            throw new SessionExpiredException();
        }

        session.confirm(command.userId());

        // Update Smart Bin cap stats via ACL
        externalDevicesService.addCaps(session.getSmartBinId(), session.getCapCount());

        // Credit citizen points/caps via ACL
        externalUserProfileService.addPointsAndCaps(
                command.userId(), session.getPointsEarned(), session.getCapCount());

        // Record on blockchain (no-op if disabled)
        blockchainPort.recordSession(session).ifPresent(session::attachBlockchainTx);

        var saved = sessionRepository.save(session);

        // Notify other contexts (e.g. mqtt opens the bin gate). sessions stays decoupled:
        // it only publishes a neutral domain event; whether anyone listens is not its concern.
        eventPublisher.publishEvent(new SessionConfirmedEvent(saved.getSmartBinId(), command.userId()));

        return Optional.of(saved);
    }

    @Override
    @Transactional
    public Optional<RecyclingSession> handle(CancelRecyclingSessionCommand command) {
        var session = sessionRepository.findById(command.sessionId())
                .orElseThrow(RecyclingSessionNotFoundException::new);
        if (!session.isPending()) {
            throw new InvalidSessionStateException();
        }
        session.cancel();
        return Optional.of(sessionRepository.save(session));
    }
}

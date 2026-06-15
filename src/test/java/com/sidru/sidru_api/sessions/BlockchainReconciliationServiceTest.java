package com.sidru.sidru_api.sessions;

import com.sidru.sidru_api.sessions.application.internal.outboundservices.blockchain.BlockchainPort;
import com.sidru.sidru_api.sessions.application.internal.scheduling.BlockchainReconciliationService;
import com.sidru.sidru_api.sessions.domain.model.aggregates.RecyclingSession;
import com.sidru.sidru_api.sessions.domain.model.valueobjects.SessionStatus;
import com.sidru.sidru_api.sessions.infrastructure.persistence.jpa.repositories.RecyclingSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Job de reconciliación de mints fallidos (§2.2). Verifica que: (1) con blockchain
 * deshabilitado no hace nada; (2) reintenta el mint de sesiones CONFIRMED sin tx y
 * adjunta el hash cuando {@code recordSession} responde; (3) deja la sesión para el
 * próximo tick si el mint sigue fallando. Todo mockeado (sin DB ni red).
 */
class BlockchainReconciliationServiceTest {

    private RecyclingSessionRepository sessionRepository;
    private BlockchainPort blockchainPort;

    @BeforeEach
    void setUp() {
        sessionRepository = mock(RecyclingSessionRepository.class);
        blockchainPort = mock(BlockchainPort.class);
    }

    private RecyclingSession confirmedSessionWithoutTx() {
        var session = new RecyclingSession(3L, 1, 25.0, 10, LocalDateTime.now().plusMinutes(15));
        session.confirm(6L); // -> CONFIRMED, userId=6, blockchainTxHash=null
        return session;
    }

    @Test
    void conBlockchainDeshabilitadoNoHaceNada() {
        var service = new BlockchainReconciliationService(sessionRepository, blockchainPort, false);

        service.reconcilePendingMints();

        verifyNoInteractions(sessionRepository, blockchainPort);
    }

    @Test
    void reintentaElMintYAdjuntaElHashCuandoRecordSessionResponde() {
        var service = new BlockchainReconciliationService(sessionRepository, blockchainPort, true);
        var session = confirmedSessionWithoutTx();
        when(sessionRepository.findTop50ByStatusAndBlockchainTxHashIsNullOrderByIdAsc(SessionStatus.CONFIRMED))
                .thenReturn(List.of(session));
        when(blockchainPort.recordSession(session)).thenReturn(Optional.of("0xrecon"));

        service.reconcilePendingMints();

        assertEquals("0xrecon", session.getBlockchainTxHash());
        verify(sessionRepository).save(session);
    }

    @Test
    void dejaLaSesionParaElProximoTickSiElMintSigueFallando() {
        var service = new BlockchainReconciliationService(sessionRepository, blockchainPort, true);
        var session = confirmedSessionWithoutTx();
        when(sessionRepository.findTop50ByStatusAndBlockchainTxHashIsNullOrderByIdAsc(SessionStatus.CONFIRMED))
                .thenReturn(List.of(session));
        when(blockchainPort.recordSession(session)).thenReturn(Optional.empty());

        service.reconcilePendingMints();

        assertNull(session.getBlockchainTxHash());
        verify(sessionRepository, never()).save(any());
    }
}

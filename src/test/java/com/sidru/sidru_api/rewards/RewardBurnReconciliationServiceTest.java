package com.sidru.sidru_api.rewards;

import com.sidru.sidru_api.rewards.application.internal.outboundservices.acl.ExternalBlockchainService;
import com.sidru.sidru_api.rewards.application.internal.scheduling.RewardBurnReconciliationService;
import com.sidru.sidru_api.rewards.domain.model.aggregates.PointTransaction;
import com.sidru.sidru_api.rewards.domain.model.valueobjects.TransactionType;
import com.sidru.sidru_api.rewards.infrastructure.persistence.jpa.repositories.PointTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Job de reconciliación de quemas fallidas (§ mejora de la quema). Verifica que: (1) con
 * blockchain deshabilitado no hace nada; (2) reintenta la quema de un REDEEM sin hash y
 * adjunta el hash; (3) deja la transacción para el próximo tick si la quema sigue fallando.
 * Todo mockeado (sin DB ni red).
 */
class RewardBurnReconciliationServiceTest {

    private static final Long USER_ID = 6L;
    private static final int POINTS_COST = 500;

    private PointTransactionRepository transactionRepository;
    private ExternalBlockchainService externalBlockchainService;

    @BeforeEach
    void setUp() {
        transactionRepository = mock(PointTransactionRepository.class);
        externalBlockchainService = mock(ExternalBlockchainService.class);
    }

    private PointTransaction pendingRedemption() {
        // REDEEM sin blockchainTxHash (quema pendiente/fallida).
        return PointTransaction.redeem(USER_ID, POINTS_COST, 1L, "Botella reutilizable");
    }

    @Test
    void conBlockchainDeshabilitadoNoHaceNada() {
        var service = new RewardBurnReconciliationService(
                transactionRepository, externalBlockchainService, false);

        service.reconcilePendingBurns();

        verifyNoInteractions(transactionRepository, externalBlockchainService);
    }

    @Test
    void reintentaLaQuemaYAdjuntaElHashCuandoExternalResponde() {
        var service = new RewardBurnReconciliationService(
                transactionRepository, externalBlockchainService, true);
        var tx = pendingRedemption();
        when(transactionRepository
                .findTop50ByTypeAndBlockchainTxHashIsNullOrderByIdAsc(TransactionType.REDEEM))
                .thenReturn(List.of(tx));
        when(externalBlockchainService.burnForRedemption(eq(USER_ID), eq(POINTS_COST), any()))
                .thenReturn(Optional.of("0xburnrecon"));

        service.reconcilePendingBurns();

        assertEquals("0xburnrecon", tx.getBlockchainTxHash());
        verify(transactionRepository).save(tx);
    }

    @Test
    void dejaLaTransaccionParaElProximoTickSiLaQuemaSigueFallando() {
        var service = new RewardBurnReconciliationService(
                transactionRepository, externalBlockchainService, true);
        var tx = pendingRedemption();
        when(transactionRepository
                .findTop50ByTypeAndBlockchainTxHashIsNullOrderByIdAsc(TransactionType.REDEEM))
                .thenReturn(List.of(tx));
        when(externalBlockchainService.burnForRedemption(eq(USER_ID), eq(POINTS_COST), any()))
                .thenReturn(Optional.empty());

        service.reconcilePendingBurns();

        assertNull(tx.getBlockchainTxHash());
        verify(transactionRepository, never()).save(any());
    }
}

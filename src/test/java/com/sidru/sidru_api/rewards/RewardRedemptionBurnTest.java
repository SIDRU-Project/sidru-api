package com.sidru.sidru_api.rewards;

import com.sidru.sidru_api.rewards.application.internal.commandservices.RewardCommandServiceImpl;
import com.sidru.sidru_api.rewards.application.internal.outboundservices.acl.ExternalBlockchainService;
import com.sidru.sidru_api.rewards.application.internal.outboundservices.acl.ExternalUserProfileService;
import com.sidru.sidru_api.rewards.domain.model.aggregates.PointTransaction;
import com.sidru.sidru_api.rewards.domain.model.aggregates.Reward;
import com.sidru.sidru_api.rewards.domain.model.commands.RedeemRewardCommand;
import com.sidru.sidru_api.rewards.domain.model.exceptions.InsufficientPointsToRedeemException;
import com.sidru.sidru_api.rewards.domain.model.valueobjects.TransactionType;
import com.sidru.sidru_api.rewards.infrastructure.persistence.jpa.repositories.PointTransactionRepository;
import com.sidru.sidru_api.rewards.infrastructure.persistence.jpa.repositories.RewardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cableado del canje de recompensas con la quema on-chain de CTC (§2.1).
 *
 * <p>Verifica que: (1) un canje exitoso dispara {@code burnForRedemption} con
 * {@code (userId, pointsCost)} y adjunta el hash; (2) si la quema no está disponible
 * (blockchain off / fallo best-effort), el canje off-chain igual procede; (3) si no hay
 * puntos suficientes no se descuenta nada ni se intenta quemar. Todo mockeado (sin DB ni red).
 */
class RewardRedemptionBurnTest {

    private static final Long USER_ID = 6L;
    private static final Long REWARD_ID = 1L;
    private static final int POINTS_COST = 500;

    private RewardRepository rewardRepository;
    private PointTransactionRepository transactionRepository;
    private ExternalUserProfileService externalUserProfileService;
    private ExternalBlockchainService externalBlockchainService;
    private RewardCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        rewardRepository = mock(RewardRepository.class);
        transactionRepository = mock(PointTransactionRepository.class);
        externalUserProfileService = mock(ExternalUserProfileService.class);
        externalBlockchainService = mock(ExternalBlockchainService.class);
        service = new RewardCommandServiceImpl(
                rewardRepository, transactionRepository,
                externalUserProfileService, externalBlockchainService);
    }

    private Reward stubActiveReward() {
        var reward = new Reward("Botella reutilizable basica", "desc", POINTS_COST, 10, null);
        when(rewardRepository.findById(REWARD_ID)).thenReturn(Optional.of(reward));
        return reward;
    }

    @Test
    void canjeExitosoQuemaCtcYAdjuntaElHash() {
        stubActiveReward();
        when(externalUserProfileService.fetchTotalPoints(USER_ID)).thenReturn(1000);
        when(transactionRepository.save(any(PointTransaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(externalBlockchainService.burnForRedemption(eq(USER_ID), eq(POINTS_COST), any()))
                .thenReturn(Optional.of("0xburn123"));

        var result = service.handle(new RedeemRewardCommand(USER_ID, REWARD_ID));

        // Off-chain: puntos descontados y transacción REDEEM creada.
        verify(externalUserProfileService).subtractPoints(USER_ID, POINTS_COST);
        verify(transactionRepository).save(any(PointTransaction.class));
        assertTrue(result.isPresent());
        assertEquals(TransactionType.REDEEM, result.get().getType());
        // On-chain: una sola quema con el costo en puntos y hash propagado a la tx.
        verify(externalBlockchainService, times(1)).burnForRedemption(eq(USER_ID), eq(POINTS_COST), any());
        assertEquals("0xburn123", result.get().getBlockchainTxHash());
    }

    @Test
    void elCanjeProcedeAunqueLaQuemaNoEsteDisponible() {
        stubActiveReward();
        when(externalUserProfileService.fetchTotalPoints(USER_ID)).thenReturn(1000);
        when(transactionRepository.save(any(PointTransaction.class))).thenAnswer(inv -> inv.getArgument(0));
        // Blockchain off o fallo best-effort -> empty.
        when(externalBlockchainService.burnForRedemption(eq(USER_ID), eq(POINTS_COST), any()))
                .thenReturn(Optional.empty());

        var result = service.handle(new RedeemRewardCommand(USER_ID, REWARD_ID));

        assertTrue(result.isPresent());
        assertNull(result.get().getBlockchainTxHash());
        // El flujo off-chain NO se rompe: los puntos se descontaron igual.
        verify(externalUserProfileService).subtractPoints(USER_ID, POINTS_COST);
    }

    @Test
    void sinPuntosSuficientesNiSeDescuentaNiSeQuema() {
        stubActiveReward();
        when(externalUserProfileService.fetchTotalPoints(USER_ID)).thenReturn(100); // < 500

        assertThrows(InsufficientPointsToRedeemException.class,
                () -> service.handle(new RedeemRewardCommand(USER_ID, REWARD_ID)));

        verify(externalUserProfileService, never()).subtractPoints(any(), anyInt());
        verify(transactionRepository, never()).save(any());
        verify(externalBlockchainService, never()).burnForRedemption(any(), anyInt(), any());
    }
}

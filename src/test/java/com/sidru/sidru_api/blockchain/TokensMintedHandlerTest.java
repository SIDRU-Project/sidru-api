package com.sidru.sidru_api.blockchain;

import com.sidru.sidru_api.blockchain.application.internal.eventhandlers.TokensMintedHandler;
import com.sidru.sidru_api.blockchain.domain.model.aggregates.BlockchainTransaction;
import com.sidru.sidru_api.blockchain.infrastructure.persistence.jpa.repositories.BlockchainTransactionRepository;
import com.sidru.sidru_api.notifications.interfaces.acl.NotificationContextFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests (no DB, no network) for {@link TokensMintedHandler} (US-BC-07 / US-39).
 * All collaborators are mocked.
 */
class TokensMintedHandlerTest {

    private static final Long SESSION_ID = 42L;
    private static final Long USER_ID = 7L;
    private static final String CUSTODIAL = "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed";
    // 10 CTC in wei.
    private static final BigInteger AMOUNT = BigInteger.TEN.multiply(BigInteger.TEN.pow(18));

    private BlockchainTransactionRepository txRepository;
    private NotificationContextFacade notificationContextFacade;
    private TokensMintedHandler handler;

    @BeforeEach
    void setUp() {
        txRepository = mock(BlockchainTransactionRepository.class);
        notificationContextFacade = mock(NotificationContextFacade.class);
        handler = new TokensMintedHandler(txRepository, notificationContextFacade);
    }

    @Test
    void marcaConfirmadaYNotificaCuandoLaTxEstaPendiente() {
        BlockchainTransaction tx = new BlockchainTransaction(
                SESSION_ID, USER_ID, "0xhash", "polygon-amoy", false, "payload");
        when(txRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.of(tx));
        when(txRepository.save(any(BlockchainTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

        handler.onTokensMinted(SESSION_ID, CUSTODIAL, AMOUNT);

        assertTrue(tx.isConfirmed(), "la tx debe quedar confirmada");
        verify(txRepository, times(1)).save(tx);
        verify(notificationContextFacade, times(1)).notifyUser(eq(USER_ID), anyString(), anyString());
    }

    @Test
    void idempotente_noNotificaSiLaTxYaEstabaConfirmada() {
        BlockchainTransaction tx = new BlockchainTransaction(
                SESSION_ID, USER_ID, "0xhash", "polygon-amoy", true, "payload");
        when(txRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.of(tx));

        handler.onTokensMinted(SESSION_ID, CUSTODIAL, AMOUNT);

        verify(txRepository, never()).save(any());
        verify(notificationContextFacade, never()).notifyUser(any(), anyString(), anyString());
    }

    @Test
    void sinTxParaLaSesion_noLanzaNiNotifica() {
        when(txRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> handler.onTokensMinted(SESSION_ID, CUSTODIAL, AMOUNT));

        verify(txRepository, never()).save(any());
        verify(notificationContextFacade, never()).notifyUser(any(), anyString(), anyString());
    }
}

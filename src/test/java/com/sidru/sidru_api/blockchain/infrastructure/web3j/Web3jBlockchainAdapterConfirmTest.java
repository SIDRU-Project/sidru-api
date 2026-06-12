package com.sidru.sidru_api.blockchain.infrastructure.web3j;

import com.sidru.sidru_api.blockchain.application.internal.custodial.CustodialWalletService;
import com.sidru.sidru_api.blockchain.domain.model.aggregates.BlockchainTransaction;
import com.sidru.sidru_api.blockchain.infrastructure.persistence.jpa.repositories.BlockchainTransactionRepository;
import com.sidru.sidru_api.blockchain.infrastructure.web3j.config.BlockchainProperties;
import com.sidru.sidru_api.notifications.interfaces.acl.NotificationContextFacade;
import com.sidru.sidru_api.sessions.domain.model.aggregates.RecyclingSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the receipt-driven confirmation path of {@link Web3jBlockchainAdapter}
 * (E2E fix): a status-OK mint receipt must persist a confirmed BlockchainTransaction and
 * fire a best-effort FCM, without depending on the TokensMinted event listener. No network
 * or Postgres: contract, wallet, repository, notifications and properties are mocked.
 */
@ExtendWith(MockitoExtension.class)
class Web3jBlockchainAdapterConfirmTest {

    private static final Long SESSION_ID = 42L;
    private static final Long USER_ID = 7L;
    private static final int POINTS = 5;
    private static final String CUSTODIAL_ADDRESS = "0x000000000000000000000000000000000000dEaD";
    private static final String TX_HASH = "0xabc";

    @Mock private BlockchainTransactionRepository txRepository;
    @Mock private CustodialWalletService custodialWalletService;
    @Mock private ChapaTuCriptoContract contract;
    @Mock private BlockchainProperties properties;
    @Mock private NotificationContextFacade notificationContextFacade;

    private Web3jBlockchainAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new Web3jBlockchainAdapter(
                txRepository, custodialWalletService, contract, properties, notificationContextFacade);
    }

    private RecyclingSession session() {
        RecyclingSession session = new RecyclingSession(
                1L, 10, 250.0, POINTS, LocalDateTime.now().plusMinutes(15));
        session.setId(SESSION_ID);
        session.setUserId(USER_ID);
        return session;
    }

    @Test
    void recordSession_onStatusOkReceipt_persistsConfirmedAndNotifies() throws Exception {
        when(properties.isEnabled()).thenReturn(true);
        when(txRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.empty());
        when(custodialWalletService.addressFor(USER_ID)).thenReturn(CUSTODIAL_ADDRESS);

        TransactionReceipt receipt = mock();
        when(receipt.isStatusOK()).thenReturn(true);
        when(receipt.getTransactionHash()).thenReturn(TX_HASH);
        when(contract.recordAndReward(eq(CUSTODIAL_ADDRESS), any(), any(), any())).thenReturn(receipt);

        Optional<String> result = adapter.recordSession(session());

        assertThat(result).contains(TX_HASH);

        ArgumentCaptor<BlockchainTransaction> captor = ArgumentCaptor.forClass(BlockchainTransaction.class);
        verify(txRepository).save(captor.capture());
        BlockchainTransaction saved = captor.getValue();
        assertThat(saved.getSessionId()).isEqualTo(SESSION_ID);
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getTxHash()).isEqualTo(TX_HASH);
        assertThat(saved.isConfirmed()).isTrue();

        verify(notificationContextFacade).notifyUser(eq(USER_ID), anyString(), anyString());
    }

    @Test
    void recordSession_disabled_returnsEmptyAndTouchesNothing() {
        when(properties.isEnabled()).thenReturn(false);

        Optional<String> result = adapter.recordSession(session());

        assertThat(result).isEmpty();
        verifyNoInteractions(contract, custodialWalletService, notificationContextFacade);
        verify(txRepository, never()).save(any());
    }

    @Test
    void recordSession_existingTx_reusesHashWithoutNotifying() {
        when(properties.isEnabled()).thenReturn(true);
        BlockchainTransaction existing = new BlockchainTransaction(
                SESSION_ID, USER_ID, TX_HASH, "polygon-amoy", true, "caps=10;points=5");
        when(txRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.of(existing));

        Optional<String> result = adapter.recordSession(session());

        assertThat(result).contains(TX_HASH);
        verify(txRepository, never()).save(any());
        verifyNoInteractions(contract, custodialWalletService, notificationContextFacade);
    }

    @Test
    void recordSession_revertedReceipt_doesNotConfirmNorNotify() throws Exception {
        when(properties.isEnabled()).thenReturn(true);
        when(txRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.empty());
        when(custodialWalletService.addressFor(USER_ID)).thenReturn(CUSTODIAL_ADDRESS);

        TransactionReceipt receipt = mock();
        when(receipt.isStatusOK()).thenReturn(false);
        when(contract.recordAndReward(anyString(), any(), any(), any())).thenReturn(receipt);

        Optional<String> result = adapter.recordSession(session());

        assertThat(result).isEmpty();
        verify(txRepository, never()).save(any());
        verify(notificationContextFacade, never()).notifyUser(anyLong(), anyString(), anyString());
    }
}

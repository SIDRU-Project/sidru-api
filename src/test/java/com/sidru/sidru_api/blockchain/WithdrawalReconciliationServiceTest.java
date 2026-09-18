package com.sidru.sidru_api.blockchain;

import com.sidru.sidru_api.blockchain.application.internal.commandservices.WithdrawalCommandService;
import com.sidru.sidru_api.blockchain.application.internal.commandservices.WithdrawalNotifier;
import com.sidru.sidru_api.blockchain.application.internal.scheduling.WithdrawalReconciliationService;
import com.sidru.sidru_api.blockchain.domain.model.aggregates.WithdrawalRequest;
import com.sidru.sidru_api.blockchain.domain.model.valueobjects.WithdrawalMode;
import com.sidru.sidru_api.blockchain.domain.model.valueobjects.WithdrawalStatus;
import com.sidru.sidru_api.blockchain.infrastructure.persistence.jpa.repositories.WithdrawalRequestRepository;
import com.sidru.sidru_api.blockchain.infrastructure.web3j.ChapaTuCriptoContract;
import com.sidru.sidru_api.blockchain.infrastructure.web3j.config.WithdrawalProperties;
import com.sidru.sidru_api.users.interfaces.acl.UserProfileContextFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * WithdrawalReconciliationService (US-MN-04): los tres desenlaces de la máquina de estados
 * (design.md §4) y que un fallo del eth_call de withdrawalProcessed no reviente el job.
 */
class WithdrawalReconciliationServiceTest {

    private static final String ADDR = "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed";

    private WithdrawalRequestRepository repository;
    private ChapaTuCriptoContract contract;
    private WithdrawalCommandService withdrawalCommandService;
    private WithdrawalProperties withdrawalProperties;
    private UserProfileContextFacade userProfileContextFacade;
    private WithdrawalNotifier notifier;
    private WithdrawalReconciliationService service;

    @BeforeEach
    void setUp() {
        repository = mock(WithdrawalRequestRepository.class);
        contract = mock(ChapaTuCriptoContract.class);
        withdrawalCommandService = mock(WithdrawalCommandService.class);
        withdrawalProperties = new WithdrawalProperties();
        withdrawalProperties.setMaxAttempts(3);
        withdrawalProperties.getReconciliation().setGraceSeconds(90);
        userProfileContextFacade = mock(UserProfileContextFacade.class);
        notifier = mock(WithdrawalNotifier.class);
        service = new WithdrawalReconciliationService(repository, contract, withdrawalCommandService,
                withdrawalProperties, userProfileContextFacade, notifier);

        when(repository.save(any(WithdrawalRequest.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private WithdrawalRequest requestConId(long id) {
        WithdrawalRequest request =
                new WithdrawalRequest(7L, ADDR, 800, "800000000000000000000", WithdrawalMode.CTC);
        request.setId(id);
        return request;
    }

    private void stubCandidates(WithdrawalRequest... requests) {
        when(repository.findTop50ByStatusAndUpdatedAtBeforeOrderByIdAsc(
                eq(WithdrawalStatus.EN_PROCESO), any(LocalDateTime.class)))
                .thenReturn(List.of(requests));
    }

    @Test
    void withdrawalProcessedTrueCompletaDesdeLaCadenaYNotificaSegunElModo() throws Exception {
        WithdrawalRequest request = requestConId(10L);
        stubCandidates(request);
        when(contract.withdrawalProcessed(BigInteger.valueOf(10L))).thenReturn(true);

        service.reconcile();

        assertEquals(WithdrawalStatus.COMPLETADO, request.getStatus());
        assertEquals("recorded:10", request.getTxHash());
        verify(notifier).notifyCompleted(request);
        verify(repository).save(request);
        verify(withdrawalCommandService, never()).submit(any());
    }

    @Test
    void attemptsMenorQueMaxReintentaViaSubmit() throws Exception {
        WithdrawalRequest request = requestConId(11L); // attempts = 0 < max 3
        stubCandidates(request);
        when(contract.withdrawalProcessed(BigInteger.valueOf(11L))).thenReturn(false);

        service.reconcile();

        verify(withdrawalCommandService).submit(request);
        verify(userProfileContextFacade, never()).refundPoints(any(), anyInt());
        verifyNoInteractions(notifier);
    }

    @Test
    void attemptsAgotadosFallaYDevuelveLosPuntos() throws Exception {
        WithdrawalRequest request = requestConId(12L);
        request.markAttempt();
        request.markAttempt();
        request.markAttempt(); // attempts = 3 == max: se agotó el presupuesto
        stubCandidates(request);
        when(contract.withdrawalProcessed(BigInteger.valueOf(12L))).thenReturn(false);

        service.reconcile();

        assertEquals(WithdrawalStatus.FALLIDO, request.getStatus());
        assertNotNull(request.getRefundedAt());
        verify(userProfileContextFacade).refundPoints(request.getUserId(), request.getPoints());
        verify(notifier).notifyFailed(request);
        verify(withdrawalCommandService, never()).submit(any());
    }

    @Test
    void unFalloDelEthCallDeWithdrawalProcessedNoRevientaElJob() throws Exception {
        WithdrawalRequest request = requestConId(13L); // attempts = 0 < max
        stubCandidates(request);
        when(contract.withdrawalProcessed(BigInteger.valueOf(13L))).thenThrow(new IOException("rpc down"));

        service.reconcile();

        // El fallo del eth_call se trata como "no confirmado todavia": sigue el flujo normal.
        verify(withdrawalCommandService).submit(request);
    }
}

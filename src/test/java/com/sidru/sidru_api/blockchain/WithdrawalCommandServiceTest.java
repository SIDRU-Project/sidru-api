package com.sidru.sidru_api.blockchain;

import com.sidru.sidru_api.blockchain.application.internal.commandservices.WithdrawalCommandService;
import com.sidru.sidru_api.blockchain.application.internal.custodial.CustodialWalletService;
import com.sidru.sidru_api.blockchain.domain.model.aggregates.WithdrawalRequest;
import com.sidru.sidru_api.blockchain.domain.model.exceptions.WithdrawalInProgressException;
import com.sidru.sidru_api.blockchain.domain.model.valueobjects.WithdrawalStatus;
import com.sidru.sidru_api.blockchain.infrastructure.persistence.jpa.repositories.WithdrawalRequestRepository;
import com.sidru.sidru_api.blockchain.infrastructure.web3j.ChapaTuCriptoContract;
import com.sidru.sidru_api.blockchain.infrastructure.web3j.config.BlockchainProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Idempotencia del retiro (RN-BC-07): no se permite un segundo retiro mientras hay
 * uno EN_PROCESO para el mismo usuario. Sin DB ni red (todo mockeado).
 */
class WithdrawalCommandServiceTest {

    private static final Long USER_ID = 99L;
    // Dirección EIP-55 válida canónica (ejemplo de la especificación EIP-55).
    private static final String VALID_ADDR = "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed";

    private CustodialWalletService custodialWalletService;
    private ChapaTuCriptoContract contract;
    private WithdrawalRequestRepository repository;
    private BlockchainProperties properties;
    private WithdrawalCommandService service;

    @BeforeEach
    void setUp() {
        custodialWalletService = mock(CustodialWalletService.class);
        contract = mock(ChapaTuCriptoContract.class);
        repository = mock(WithdrawalRequestRepository.class);
        properties = new BlockchainProperties();
        properties.setEnabled(true);
        service = new WithdrawalCommandService(custodialWalletService, contract, repository, properties);
    }

    @Test
    void rechazaUnSegundoRetiroMientrasHayUnoEnProceso() throws Exception {
        // Ya hay un retiro EN_PROCESO para el usuario.
        WithdrawalRequest inProgress = new WithdrawalRequest(USER_ID, VALID_ADDR, "1000");
        when(repository.findByUserIdAndStatus(USER_ID, WithdrawalStatus.EN_PROCESO))
                .thenReturn(List.of(inProgress));

        assertThrows(WithdrawalInProgressException.class,
                () -> service.withdraw(USER_ID, VALID_ADDR));

        // No se inicia ninguna tx on-chain ni se persiste un segundo retiro.
        verify(contract, never()).withdrawTo(anyString(), anyString(), any());
        verify(repository, never()).save(any());
    }

    @Test
    void primerRetiroProcedeYSeCompletaConUnaSolaTx() throws Exception {
        when(repository.findByUserIdAndStatus(USER_ID, WithdrawalStatus.EN_PROCESO))
                .thenReturn(List.of());
        when(custodialWalletService.addressFor(USER_ID)).thenReturn(VALID_ADDR);
        // 500 CTC de saldo custodial.
        when(contract.balanceOf(VALID_ADDR)).thenReturn(new BigInteger("500000000000000000000"));
        when(repository.save(any(WithdrawalRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        TransactionReceipt receipt = mock(TransactionReceipt.class);
        when(receipt.getTransactionHash()).thenReturn("0xabc123");
        when(contract.withdrawTo(eq(VALID_ADDR), eq(VALID_ADDR), any())).thenReturn(receipt);

        WithdrawalRequest result = service.withdraw(USER_ID, VALID_ADDR);

        // Exactamente una tx de retiro y estado COMPLETADO.
        verify(contract, times(1)).withdrawTo(eq(VALID_ADDR), eq(VALID_ADDR), any());
        assertEquals(WithdrawalStatus.COMPLETADO, result.getStatus());
        assertEquals("0xabc123", result.getTxHash());
    }
}

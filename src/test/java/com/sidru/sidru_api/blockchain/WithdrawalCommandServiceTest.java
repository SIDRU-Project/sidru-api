package com.sidru.sidru_api.blockchain;

import com.sidru.sidru_api.blockchain.application.internal.commandservices.WithdrawalCommandService;
import com.sidru.sidru_api.blockchain.application.internal.commandservices.WithdrawalNotifier;
import com.sidru.sidru_api.blockchain.domain.model.aggregates.WithdrawalRequest;
import com.sidru.sidru_api.blockchain.domain.model.exceptions.BelowMinimumWithdrawalException;
import com.sidru.sidru_api.blockchain.domain.model.exceptions.InvalidWithdrawalAddressException;
import com.sidru.sidru_api.blockchain.domain.model.exceptions.NoBalanceToWithdrawException;
import com.sidru.sidru_api.blockchain.domain.model.exceptions.WithdrawalInProgressException;
import com.sidru.sidru_api.blockchain.domain.model.exceptions.WithdrawalsDisabledException;
import com.sidru.sidru_api.blockchain.domain.model.valueobjects.WithdrawalMode;
import com.sidru.sidru_api.blockchain.domain.model.valueobjects.WithdrawalStatus;
import com.sidru.sidru_api.blockchain.infrastructure.persistence.jpa.repositories.WithdrawalRequestRepository;
import com.sidru.sidru_api.blockchain.infrastructure.web3j.ChapaTuCriptoContract;
import com.sidru.sidru_api.blockchain.infrastructure.web3j.ContractRevertException;
import com.sidru.sidru_api.blockchain.infrastructure.web3j.config.WithdrawalProperties;
import com.sidru.sidru_api.users.domain.model.exceptions.InsufficientPointsException;
import com.sidru.sidru_api.users.interfaces.acl.UserProfileContextFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Retiro desde puntos (US-MN-01 a US-MN-04): validaciones de withdraw() y cada rama de
 * submit() de la máquina de estados (design.md §4-5). Sin BD ni red: repositorio, contrato
 * y notificador mockeados; el TransactionTemplate usa un PlatformTransactionManager de
 * prueba que ejecuta el callback directo (sin transacción real — el orden lock→guard en sí
 * se ejercita end-to-end con concurrencia real en CpMn08RetiroConcurrenciaTest).
 */
class WithdrawalCommandServiceTest {

    private static final Long USER_ID = 99L;
    private static final String VALID_ADDR = "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed";
    private static final int POINTS = 800;

    private UserProfileContextFacade userProfileContextFacade;
    private ChapaTuCriptoContract contract;
    private WithdrawalRequestRepository repository;
    private WithdrawalProperties withdrawalProperties;
    private WithdrawalNotifier notifier;
    private WithdrawalCommandService service;

    @BeforeEach
    void setUp() {
        userProfileContextFacade = mock(UserProfileContextFacade.class);
        contract = mock(ChapaTuCriptoContract.class);
        repository = mock(WithdrawalRequestRepository.class);
        withdrawalProperties = new WithdrawalProperties();
        withdrawalProperties.setEnabled(true);
        withdrawalProperties.setMinPoints(500);
        withdrawalProperties.setMaxAttempts(3);
        notifier = mock(WithdrawalNotifier.class);
        service = new WithdrawalCommandService(userProfileContextFacade, contract, repository,
                withdrawalProperties, notifier, fakeTransactionManager());

        when(repository.save(any(WithdrawalRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(repository.findByUserIdAndStatus(USER_ID, WithdrawalStatus.EN_PROCESO))
                .thenReturn(List.of());
    }

    /** Ejecuta el callback del TransactionTemplate directo, sin abrir una transacción real. */
    private static PlatformTransactionManager fakeTransactionManager() {
        return new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {}

            @Override
            public void rollback(TransactionStatus status) {}
        };
    }

    private WithdrawalRequest newRequest(WithdrawalMode mode) {
        BigInteger amountWei = BigInteger.valueOf(POINTS).multiply(BigInteger.TEN.pow(18));
        WithdrawalRequest request =
                new WithdrawalRequest(USER_ID, VALID_ADDR, POINTS, amountWei.toString(), mode);
        request.setId(1L);
        return request;
    }

    // ------------------------------------------------------------ withdraw(): validaciones

    @Test
    void retirosDeshabilitadosLanzaWithdrawalsDisabled() {
        withdrawalProperties.setEnabled(false);

        assertThrows(WithdrawalsDisabledException.class,
                () -> service.withdraw(USER_ID, VALID_ADDR, POINTS, WithdrawalMode.CTC));

        verifyNoInteractions(contract, repository);
    }

    @Test
    void direccionInvalidaLanzaInvalidWithdrawalAddress() {
        assertThrows(InvalidWithdrawalAddressException.class,
                () -> service.withdraw(USER_ID, "0xNoEsUnaDireccion", POINTS, WithdrawalMode.CTC));

        verifyNoInteractions(contract, repository);
    }

    @Test
    void bajoElMinimoLanzaBelowMinimumWithdrawal() {
        assertThrows(BelowMinimumWithdrawalException.class,
                () -> service.withdraw(USER_ID, VALID_ADDR, 499, WithdrawalMode.CTC));

        verifyNoInteractions(contract, repository);
    }

    @Test
    void guardEnProcesoLanzaWithdrawalInProgress() {
        // El lock (subtractPointsLocked) se toma ANTES del guard: sucede igual, y solo
        // despues se descubre el EN_PROCESO y se aborta (design.md §5).
        when(repository.findByUserIdAndStatus(USER_ID, WithdrawalStatus.EN_PROCESO))
                .thenReturn(List.of(newRequest(WithdrawalMode.CTC)));

        assertThrows(WithdrawalInProgressException.class,
                () -> service.withdraw(USER_ID, VALID_ADDR, POINTS, WithdrawalMode.CTC));

        verify(userProfileContextFacade).subtractPointsLocked(USER_ID, POINTS);
        verify(repository, never()).save(any());
        verifyNoInteractions(contract);
    }

    @Test
    void puntosInsuficientesLanzaNoBalanceToWithdraw() {
        doThrow(new InsufficientPointsException())
                .when(userProfileContextFacade).subtractPointsLocked(USER_ID, POINTS);

        assertThrows(NoBalanceToWithdrawException.class,
                () -> service.withdraw(USER_ID, VALID_ADDR, POINTS, WithdrawalMode.CTC));

        // El guard de EN_PROCESO ni se llega a evaluar: el debito fallo primero.
        verify(repository, never()).findByUserIdAndStatus(any(), any());
        verify(repository, never()).save(any());
        verifyNoInteractions(contract);
    }

    // ------------------------------------------------------------ submit(): ramas

    @Test
    void submitConReciboOkEnCtcCompletaYNotifica() throws Exception {
        WithdrawalRequest request = newRequest(WithdrawalMode.CTC);
        TransactionReceipt receipt = mock(TransactionReceipt.class);
        when(receipt.getTransactionHash()).thenReturn("0xabc123");
        when(contract.mintWithdrawal(eq(VALID_ADDR), eq(BigInteger.valueOf(1L)), any()))
                .thenReturn(receipt);

        service.submit(request);

        assertEquals(WithdrawalStatus.COMPLETADO, request.getStatus());
        assertEquals("0xabc123", request.getTxHash());
        assertEquals(1, request.getAttempts());
        verify(notifier).notifyCompleted(request);
        verify(notifier, never()).notifyFailed(any());
        verify(contract, never()).decodeReserveOut(any());
        verify(repository).save(request);
    }

    @Test
    void submitConReciboOkEnUsdcDecodificaReserveOut() throws Exception {
        WithdrawalRequest request = newRequest(WithdrawalMode.USDC);
        TransactionReceipt receipt = mock(TransactionReceipt.class);
        when(receipt.getTransactionHash()).thenReturn("0xdef456");
        when(contract.payoutReserve(eq(VALID_ADDR), eq(BigInteger.valueOf(1L)), any()))
                .thenReturn(receipt);
        when(contract.decodeReserveOut(receipt)).thenReturn(BigInteger.valueOf(2_777_777L));

        service.submit(request);

        assertEquals(WithdrawalStatus.COMPLETADO, request.getStatus());
        assertEquals("2777777", request.getReserveOut());
        verify(notifier).notifyCompleted(request);
    }

    @Test
    void submitConAlreadyProcessedCompletaDesdeLaCadena() throws Exception {
        WithdrawalRequest request = newRequest(WithdrawalMode.CTC);
        Exception reverted = new ContractRevertException("0xWithdrawalAlreadyProcessedSelector");
        when(contract.mintWithdrawal(any(), any(), any())).thenThrow(reverted);
        when(contract.isWithdrawalAlreadyProcessed(reverted)).thenReturn(true);

        service.submit(request);

        assertEquals(WithdrawalStatus.COMPLETADO, request.getStatus());
        assertEquals("recorded:1", request.getTxHash());
        assertEquals(1, request.getAttempts());
        verify(notifier).notifyCompleted(request);
        verify(userProfileContextFacade, never()).refundPoints(any(), anyInt());
    }

    @Test
    void submitConInsufficientReserveNoConsumeIntentosNiNotifica() throws Exception {
        WithdrawalRequest request = newRequest(WithdrawalMode.CTC);
        Exception reverted = new ContractRevertException("0xInsufficientReserveSelector");
        when(contract.mintWithdrawal(any(), any(), any())).thenThrow(reverted);
        when(contract.isInsufficientReserve(reverted)).thenReturn(true);

        service.submit(request);

        assertEquals(WithdrawalStatus.EN_PROCESO, request.getStatus());
        assertEquals(0, request.getAttempts(), "InsufficientReserve no cuenta como intento");
        verifyNoInteractions(notifier);
        verify(userProfileContextFacade, never()).refundPoints(any(), anyInt());
        verify(repository).save(request);
    }

    @Test
    void submitConRevertConMotivoFallaYRefunda() throws Exception {
        WithdrawalRequest request = newRequest(WithdrawalMode.CTC);
        Exception reverted = new ContractRevertException("0xdeadbeef");
        when(contract.mintWithdrawal(any(), any(), any())).thenThrow(reverted);
        // isWithdrawalAlreadyProcessed / isInsufficientReserve devuelven false por defecto (mock).

        service.submit(request);

        assertEquals(WithdrawalStatus.FALLIDO, request.getStatus());
        assertEquals(1, request.getAttempts());
        assertNotNull(request.getFailureReason());
        assertNotNull(request.getRefundedAt());
        verify(userProfileContextFacade).refundPoints(USER_ID, POINTS);
        verify(notifier).notifyFailed(request);
        verify(notifier, never()).notifyCompleted(any());
    }

    @Test
    void submitConRevertSinMotivoQuedaEnProcesoReintentable() throws Exception {
        WithdrawalRequest request = newRequest(WithdrawalMode.CTC);
        Exception reverted = new ContractRevertException(null);
        when(contract.mintWithdrawal(any(), any(), any())).thenThrow(reverted);

        service.submit(request);

        assertEquals(WithdrawalStatus.EN_PROCESO, request.getStatus());
        assertEquals(1, request.getAttempts());
        assertNull(request.getFailureReason());
        verify(userProfileContextFacade, never()).refundPoints(any(), anyInt());
        verifyNoInteractions(notifier);
    }

    @Test
    void submitConIOExceptionQuedaEnProcesoReintentable() throws Exception {
        WithdrawalRequest request = newRequest(WithdrawalMode.CTC);
        when(contract.mintWithdrawal(any(), any(), any())).thenThrow(new IOException("rpc down"));

        service.submit(request);

        assertEquals(WithdrawalStatus.EN_PROCESO, request.getStatus());
        assertEquals(1, request.getAttempts());
        verify(userProfileContextFacade, never()).refundPoints(any(), anyInt());
        verifyNoInteractions(notifier);
    }

    @Test
    void refundPointsSoloSeLlamaEnElFalloDefinitivo() throws Exception {
        // Recorre las cuatro ramas de error de submit() y confirma que refundPoints
        // (Mockito, conteo exacto) solo se invoca en la del revert con motivo.
        WithdrawalRequest alreadyProcessed = newRequest(WithdrawalMode.CTC);
        Exception ap = new ContractRevertException("x");
        when(contract.mintWithdrawal(eq(VALID_ADDR), eq(BigInteger.valueOf(1L)), any())).thenThrow(ap);
        when(contract.isWithdrawalAlreadyProcessed(ap)).thenReturn(true);
        service.submit(alreadyProcessed);

        WithdrawalRequest insufficientReserve = newRequest(WithdrawalMode.CTC);
        insufficientReserve.setId(2L);
        Exception ir = new ContractRevertException("y");
        when(contract.mintWithdrawal(eq(VALID_ADDR), eq(BigInteger.valueOf(2L)), any())).thenThrow(ir);
        when(contract.isInsufficientReserve(ir)).thenReturn(true);
        service.submit(insufficientReserve);

        WithdrawalRequest ioFailure = newRequest(WithdrawalMode.CTC);
        ioFailure.setId(3L);
        when(contract.mintWithdrawal(eq(VALID_ADDR), eq(BigInteger.valueOf(3L)), any()))
                .thenThrow(new IOException("timeout"));
        service.submit(ioFailure);

        verify(userProfileContextFacade, never()).refundPoints(any(), anyInt());

        WithdrawalRequest deterministic = newRequest(WithdrawalMode.CTC);
        deterministic.setId(4L);
        when(contract.mintWithdrawal(eq(VALID_ADDR), eq(BigInteger.valueOf(4L)), any()))
                .thenThrow(new ContractRevertException("0xotromotivo"));
        service.submit(deterministic);

        verify(userProfileContextFacade, times(1)).refundPoints(USER_ID, POINTS);
    }

    // ------------------------------------------------------------ getById / lastStatus

    @Test
    void getByIdDevuelveElRetiroCuandoPerteneceAlUsuario() {
        WithdrawalRequest request = newRequest(WithdrawalMode.CTC);
        when(repository.findById(1L)).thenReturn(Optional.of(request));

        assertEquals(request, service.getById(1L, USER_ID));
    }

    @Test
    void lastStatusDevuelveElUltimoRetiroDelUsuario() {
        WithdrawalRequest last = newRequest(WithdrawalMode.CTC);
        when(repository.findTopByUserIdOrderByIdDesc(USER_ID)).thenReturn(Optional.of(last));

        assertEquals(last, service.lastStatus(USER_ID));
    }
}

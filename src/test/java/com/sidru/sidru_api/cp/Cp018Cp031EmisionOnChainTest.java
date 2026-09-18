package com.sidru.sidru_api.cp;

import com.sidru.sidru_api.blockchain.domain.model.aggregates.BlockchainTransaction;
import com.sidru.sidru_api.blockchain.application.internal.custodial.CustodialWalletService;
import com.sidru.sidru_api.blockchain.infrastructure.persistence.jpa.repositories.BlockchainTransactionRepository;
import com.sidru.sidru_api.blockchain.infrastructure.web3j.ChapaTuCriptoContract;
import com.sidru.sidru_api.blockchain.infrastructure.web3j.Web3jBlockchainAdapter;
import com.sidru.sidru_api.blockchain.infrastructure.web3j.config.BlockchainProperties;
import com.sidru.sidru_api.notifications.interfaces.acl.NotificationContextFacade;
import com.sidru.sidru_api.sessions.domain.model.aggregates.RecyclingSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CP018 — Emision de tokens en la wallet custodial y evidencia on-chain (US-21, US-14, esc. 1).
 * CP031 — Robustez de la integracion backend-blockchain con Web3j (US-24, esc. 1).
 *
 * <p>Se ejerce el adaptador Web3j con el contrato mockeado: la red Amoy no participa. Lo que
 * se verifica es el contrato del adaptador — monto minteado, persistencia de la evidencia,
 * reintentos con backoff y ausencia de estados inconsistentes ante un fallo definitivo. La
 * comprobacion del hash en Polygonscan y la prueba bajo congestion real de la red son los
 * pasos manuales que acompanan a este caso (CP030).</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CP018/CP031 - Emision on-chain y robustez de la integracion Web3j")
class Cp018Cp031EmisionOnChainTest {

    private static final Long SESSION_ID = 4242L;
    private static final Long USER_ID = 77L;
    private static final int POINTS = 200;
    private static final String CUSTODIAL_ADDRESS = "0x000000000000000000000000000000000000dEaD";
    private static final String TX_HASH = "0xfeedfacecafebeef";
    private static final BigInteger WEI_PER_CTC = BigInteger.TEN.pow(18);
    private static final String NETWORK = "polygon-amoy";

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
        var session = new RecyclingSession(1L, 25, 500.0, POINTS, LocalDateTime.now().plusMinutes(15));
        session.setId(SESSION_ID);
        session.setUserId(USER_ID);
        return session;
    }

    /**
     * Recibo con status OK. Se construye SIEMPRE fuera de un when(...) externo: stubbear
     * dentro de otro stubbing en curso rompe a Mockito (UnfinishedStubbingException).
     */
    private TransactionReceipt okReceipt() {
        TransactionReceipt receipt = org.mockito.Mockito.mock(TransactionReceipt.class);
        org.mockito.Mockito.lenient().when(receipt.isStatusOK()).thenReturn(true);
        org.mockito.Mockito.lenient().when(receipt.getTransactionHash()).thenReturn(TX_HASH);
        return receipt;
    }

    // ------------------------------------------------------------------ CP018

    @Test
    @DisplayName("CP018 - Paso 1-3: mintea a la direccion custodial, devuelve txHash y deja la evidencia")
    void minteaALaDireccionCustodialYPersisteLaEvidencia() throws Exception {
        when(properties.isEnabled()).thenReturn(true);
        when(txRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.empty());
        when(custodialWalletService.addressFor(USER_ID)).thenReturn(CUSTODIAL_ADDRESS);
        when(contract.sessionRecorded(BigInteger.valueOf(SESSION_ID))).thenReturn(false);
        when(properties.getNetworkLabel()).thenReturn(NETWORK);
        TransactionReceipt receipt = okReceipt();
        when(contract.recordAndReward(eq(CUSTODIAL_ADDRESS), eq(BigInteger.valueOf(SESSION_ID)),
                any(), any())).thenReturn(receipt);

        // Paso 1 — Confirmar una sesion valida: el backend invoca el contrato y obtiene un txHash.
        Optional<String> result = adapter.recordSession(session());
        assertEquals(Optional.of(TX_HASH), result);

        // Paso 2 — El monto minteado es coherente con el calculo off-chain: 1 punto = 1 CTC = 10^18 wei.
        var amountCaptor = ArgumentCaptor.forClass(BigInteger.class);
        verify(contract).recordAndReward(eq(CUSTODIAL_ADDRESS), eq(BigInteger.valueOf(SESSION_ID)),
                any(), amountCaptor.capture());
        assertEquals(BigInteger.valueOf(POINTS).multiply(WEI_PER_CTC), amountCaptor.getValue(),
                "el monto on-chain debe ser pointsEarned * 10^18");

        // Paso 3 — La transaccion queda registrada de forma inmutable y trazable (hash + red).
        var txCaptor = ArgumentCaptor.forClass(BlockchainTransaction.class);
        verify(txRepository).save(txCaptor.capture());
        BlockchainTransaction persisted = txCaptor.getValue();
        assertEquals(TX_HASH, persisted.getTxHash());
        assertEquals(SESSION_ID, persisted.getSessionId());
        assertEquals(USER_ID, persisted.getUserId());
        assertEquals(NETWORK, persisted.getNetwork(),
                "la red persistida debe ser la configurada, no una constante del adaptador");
        assertTrue(persisted.isConfirmed(), "un recibo con status OK prueba la inclusion en bloque");

        // El evento de acreditacion alimenta la notificacion al ciudadano (US-39).
        verify(notificationContextFacade).notifyUser(eq(USER_ID), anyString(), anyString());
    }

    @Test
    @DisplayName("CP018 - Idempotencia: una sesion ya registrada on-chain no vuelve a mintear ni gasta gas")
    void noRemintealaSesionYaRegistradaEnLaCadena() throws Exception {
        when(properties.isEnabled()).thenReturn(true);
        when(txRepository.findBySessionId(SESSION_ID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new BlockchainTransaction(
                        SESSION_ID, USER_ID, TX_HASH, "polygon-amoy", true, "")));
        when(custodialWalletService.addressFor(USER_ID)).thenReturn(CUSTODIAL_ADDRESS);
        when(contract.sessionRecorded(BigInteger.valueOf(SESSION_ID))).thenReturn(true);

        Optional<String> result = adapter.recordSession(session());

        assertEquals(Optional.of(TX_HASH), result);
        verify(contract, never()).recordAndReward(anyString(), any(), any(), any());
    }

    // ------------------------------------------------------------------ CP031

    @Test
    @DisplayName("CP031 - Paso 2-3: ante un fallo de red reintenta con backoff hasta confirmar")
    void reintentaConBackoffAnteUnFalloDeRedYTerminaConfirmando() throws Exception {
        when(properties.isEnabled()).thenReturn(true);
        when(txRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.empty());
        when(custodialWalletService.addressFor(USER_ID)).thenReturn(CUSTODIAL_ADDRESS);
        when(contract.sessionRecorded(BigInteger.valueOf(SESSION_ID))).thenReturn(false);

        // Dos fallos de conexion con el nodo RPC y a la tercera confirma.
        TransactionReceipt receipt = okReceipt();
        when(contract.recordAndReward(anyString(), any(), any(), any()))
                .thenThrow(new java.io.IOException("connection reset by peer"))
                .thenThrow(new java.io.IOException("read timeout"))
                .thenReturn(receipt);

        long start = System.nanoTime();
        Optional<String> result = adapter.recordSession(session());
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;

        assertEquals(Optional.of(TX_HASH), result, "la transaccion debe confirmarse tras los reintentos");
        verify(contract, times(3)).recordAndReward(anyString(), any(), any(), any());

        // El backoff es exponencial (500 ms y 1000 ms entre intentos): el tiempo total lo refleja.
        assertTrue(elapsedMillis >= 1_500L,
                "se esperaba backoff exponencial acumulado >= 1500 ms, fue " + elapsedMillis + " ms");
    }

    @Test
    @DisplayName("CP031 - Paso 4: tras agotar los reintentos la sesion queda pendiente de emision, sin evidencia falsa")
    void trasElFalloDefinitivoNoDejaEstadosInconsistentes() throws Exception {
        when(properties.isEnabled()).thenReturn(true);
        when(txRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.empty());
        when(custodialWalletService.addressFor(USER_ID)).thenReturn(CUSTODIAL_ADDRESS);
        when(contract.sessionRecorded(BigInteger.valueOf(SESSION_ID))).thenReturn(false);
        when(contract.recordAndReward(anyString(), any(), any(), any()))
                .thenThrow(new java.io.IOException("nodo RPC inaccesible"));

        Optional<String> result = adapter.recordSession(session());

        // Sin hash: la sesion queda marcada como pendiente de emision para la reconciliacion.
        assertTrue(result.isEmpty(), "un fallo definitivo no debe devolver un hash inventado");
        verify(contract, times(3)).recordAndReward(anyString(), any(), any(), any());
        verify(txRepository, never()).save(any());
        verify(notificationContextFacade, never()).notifyUser(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("CP031 - Con la integracion deshabilitada no se toca la red ni se persiste nada")
    void conBlockchainDeshabilitadoNoTocaLaRed() {
        when(properties.isEnabled()).thenReturn(false);

        assertTrue(adapter.recordSession(session()).isEmpty());
        verify(txRepository, never()).save(any());
    }
}

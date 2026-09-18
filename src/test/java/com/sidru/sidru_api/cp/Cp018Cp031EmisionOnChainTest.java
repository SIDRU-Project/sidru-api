package com.sidru.sidru_api.cp;

import com.sidru.sidru_api.blockchain.application.internal.commandservices.WithdrawalCommandService;
import com.sidru.sidru_api.blockchain.application.internal.commandservices.WithdrawalNotifier;
import com.sidru.sidru_api.blockchain.application.internal.scheduling.WithdrawalReconciliationService;
import com.sidru.sidru_api.blockchain.domain.model.aggregates.WithdrawalRequest;
import com.sidru.sidru_api.blockchain.domain.model.valueobjects.WithdrawalStatus;
import com.sidru.sidru_api.blockchain.infrastructure.persistence.jpa.repositories.WithdrawalRequestRepository;
import com.sidru.sidru_api.blockchain.infrastructure.web3j.ChapaTuCriptoContract;
import com.sidru.sidru_api.blockchain.infrastructure.web3j.config.WithdrawalProperties;
import com.sidru.sidru_api.notifications.interfaces.acl.NotificationContextFacade;
import com.sidru.sidru_api.users.interfaces.acl.UserProfileContextFacade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CP018 — Emisión de CTC al retirar (US-MN-01: la emisión ya no ocurre por sesión, ocurre en
 * mintWithdrawal). CP031 — Reintentos y reconciliación del retiro (US-MN-04).
 *
 * <p>La reconciliación se ejercita instanciando {@link WithdrawalReconciliationService} a mano
 * con las mismas dependencias autowireadas del contexto (el bean real no existe en el perfil de
 * test porque {@code sidru.blockchain.enabled=false}; instanciarlo aquí evita además el
 * {@code @Scheduled} disparándose solo durante la prueba). El grace-seconds se fuerza a 0 para
 * no depender de que pase tiempo real de reloj. El umbral es inclusivo (&lt;=) para que una fila
 * actualizada en el mismo instante entre en el lote.</p>
 */
@TestPropertySource(properties = "sidru.withdrawal.reconciliation.grace-seconds=0")
@DisplayName("CP018/CP031 - Emision de CTC al retirar y reconciliacion")
class Cp018Cp031EmisionOnChainTest extends CpBaseTest {

    private static final String VALID_ADDR = "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed";
    private static final BigInteger CTC_UNIT = BigInteger.TEN.pow(18);

    @MockitoBean
    private ChapaTuCriptoContract contract;

    @MockitoBean
    private NotificationContextFacade notificationContextFacade;

    @Autowired
    private WithdrawalRequestRepository withdrawalRequestRepository;

    @Autowired
    private WithdrawalCommandService withdrawalCommandService;

    @Autowired
    private WithdrawalProperties withdrawalProperties;

    @Autowired
    private UserProfileContextFacade userProfileContextFacade;

    @Autowired
    private WithdrawalNotifier notifier;

    private WithdrawalReconciliationService reconciliationService() {
        return new WithdrawalReconciliationService(withdrawalRequestRepository, contract,
                withdrawalCommandService, withdrawalProperties, userProfileContextFacade, notifier);
    }

    // ------------------------------------------------------------------ CP018

    @Test
    @DisplayName("CP018 - mintWithdrawal se llama con el monto exacto (points x 1e18) y el retiro queda COMPLETADO")
    void mintWithdrawalConElMontoExactoYNotificacion() throws Exception {
        var citizen = newCitizen();
        givePoints(citizen, 800);

        TransactionReceipt receipt = mock(TransactionReceipt.class);
        when(receipt.getTransactionHash()).thenReturn("0xmint800");
        when(contract.mintWithdrawal(eq(VALID_ADDR), any(BigInteger.class),
                eq(BigInteger.valueOf(800).multiply(CTC_UNIT)))).thenReturn(receipt);

        String body = mockMvc.perform(apiPost("/wallet/withdraw")
                        .header("Authorization", citizen.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("toAddress", VALID_ADDR, "points", 800, "mode", "CTC"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETADO"))
                .andExpect(jsonPath("$.txHash").value("0xmint800"))
                .andReturn().getResponse().getContentAsString();

        Long id = Long.valueOf(field(body, "id", Integer.class));
        var persisted = withdrawalRequestRepository.findById(id).orElseThrow();
        assertEquals(WithdrawalStatus.COMPLETADO, persisted.getStatus());

        verify(contract).mintWithdrawal(eq(VALID_ADDR),
                eq(BigInteger.valueOf(persisted.getChainWithdrawalId())),
                eq(BigInteger.valueOf(800).multiply(CTC_UNIT)));
        verify(notificationContextFacade).notifyUser(eq(citizen.id()), eq("Retiro completado"),
                contains("800 CTC"));
    }

    // ------------------------------------------------------------------ CP031

    @Test
    @DisplayName("CP031 - Fallo de red deja EN_PROCESO; la reconciliacion lo completa via withdrawalProcessed sin reenviar")
    void falloDeRedYReconciliacionPorLecturaOnChain() throws Exception {
        var citizen = newCitizen();
        givePoints(citizen, 800);
        when(contract.mintWithdrawal(any(), any(), any())).thenThrow(new IOException("rpc down"));

        String body = mockMvc.perform(apiPost("/wallet/withdraw")
                        .header("Authorization", citizen.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("toAddress", VALID_ADDR, "points", 800, "mode", "CTC"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("EN_PROCESO"))
                .andReturn().getResponse().getContentAsString();

        Long id = Long.valueOf(field(body, "id", Integer.class));
        var created = withdrawalRequestRepository.findById(id).orElseThrow();
        assertEquals(1, created.getAttempts());

        reset(contract); // limpiamos el conteo de invocaciones antes de comprobar "no reenvia"
        when(contract.withdrawalProcessed(BigInteger.valueOf(created.getChainWithdrawalId()))).thenReturn(true);

        reconciliationService().reconcile();

        var reconciled = withdrawalRequestRepository.findById(id).orElseThrow();
        assertEquals(WithdrawalStatus.COMPLETADO, reconciled.getStatus());
        assertEquals("recorded:" + created.getChainWithdrawalId(), reconciled.getTxHash());
        verify(contract, never()).mintWithdrawal(any(), any(), any());
    }

    @Test
    @DisplayName("CP031 - Agotados los intentos, el retiro pasa a FALLIDO y devuelve los puntos integros sin tocar totalSessions")
    void agotaIntentosYDevuelveLosPuntosIntegros() throws Exception {
        var citizen = newCitizen();
        givePoints(citizen, 800);
        int sessionsAntes = userProfileRepository.findByUserId(citizen.id()).orElseThrow().getTotalSessions();
        withdrawalProperties.setMaxAttempts(3);
        when(contract.mintWithdrawal(any(), any(), any())).thenThrow(new IOException("rpc down"));

        String body = mockMvc.perform(apiPost("/wallet/withdraw")
                        .header("Authorization", citizen.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("toAddress", VALID_ADDR, "points", 800, "mode", "CTC"))))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        Long id = Long.valueOf(field(body, "id", Integer.class));

        var request = withdrawalRequestRepository.findById(id).orElseThrow();
        withdrawalCommandService.submit(request); // intento 2
        withdrawalCommandService.submit(request); // intento 3 == maxAttempts

        when(contract.withdrawalProcessed(BigInteger.valueOf(request.getChainWithdrawalId()))).thenReturn(false);
        reconciliationService().reconcile();

        var failed = withdrawalRequestRepository.findById(id).orElseThrow();
        assertEquals(WithdrawalStatus.FALLIDO, failed.getStatus());
        assertNotNull(failed.getRefundedAt());

        var profile = userProfileRepository.findByUserId(citizen.id()).orElseThrow();
        assertEquals(800, profile.getTotalPoints(), "los 800 puntos deben volver integros");
        assertEquals(sessionsAntes, profile.getTotalSessions(),
                "el refund usa refundPoints, no addPointsAndCaps: no debe tocar totalSessions");
        verify(notificationContextFacade).notifyUser(eq(citizen.id()), anyString(),
                contains("volvieron a tu cuenta"));
    }
}

package com.sidru.sidru_api.cp;

import com.sidru.sidru_api.blockchain.application.internal.commandservices.WithdrawalCommandService;
import com.sidru.sidru_api.blockchain.domain.model.valueobjects.WithdrawalStatus;
import com.sidru.sidru_api.blockchain.infrastructure.persistence.jpa.repositories.WithdrawalRequestRepository;
import com.sidru.sidru_api.blockchain.infrastructure.web3j.ChapaTuCriptoContract;
import com.sidru.sidru_api.blockchain.infrastructure.web3j.ContractRevertException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.web3j.crypto.Hash;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CP029 — Unicidad del codigo QR generado por sesion; y (requirements.md, tabla de
 * trazabilidad: "CP029 | Unicidad on-chain por withdrawalId") la idempotencia del retiro
 * cuando el backend reenvia mintWithdrawal tras no confirmar el primer envio (US-MN-04).
 * HU: US-17 · Escenario 1 · Prioridad: Alta
 * Criterio de aceptacion: Generacion de un codigo QR unico.
 *
 * Postcondicion: los 50 codigos quedan registrados en el backend con su estado correspondiente.
 */
@DisplayName("CP029 - Unicidad del codigo QR generado por sesion")
class Cp029UnicidadDelCodigoQrTest extends CpBaseTest {

    private static final int SESIONES = 50;

    @MockitoBean
    private ChapaTuCriptoContract contract;

    @Autowired
    private WithdrawalCommandService withdrawalCommandService;

    @Autowired
    private WithdrawalRequestRepository withdrawalRequestRepository;

    @Test
    @DisplayName("Paso 1-3: 50 sesiones consecutivas generan 50 codigos distintos")
    void cincuentaSesionesGeneranCincuentaCodigosDistintos() throws Exception {
        var bin = newSmartBin();

        // Paso 1 — Generar 50 sesiones consecutivas y capturar el codigo QR de cada una.
        List<String> tokens = new ArrayList<>(SESIONES);
        for (int i = 0; i < SESIONES; i++) {
            tokens.add(openSession(bin, 10 + i % 5, 30.0 + i).getQrToken());
        }
        assertEquals(SESIONES, tokens.size(), "debe emitirse un codigo por cada sesion cerrada");

        // Paso 2 — Cada codigo decodifica un sessionId distinto junto con su hash de validacion.
        Set<Long> sessionIds = new HashSet<>();
        for (String token : tokens) {
            var session = sessionRepository.findByQrToken(token).orElseThrow(
                    () -> new AssertionError("el token " + token + " debe resolver una sesion"));
            sessionIds.add(session.getId());
            assertTrue(token.matches("[0-9a-f]{32}"),
                    "el token debe ser un identificador opaco de 32 hexadecimales, fue: " + token);
        }

        // Paso 3 — No existen duplicados en el conjunto.
        assertEquals(SESIONES, new HashSet<>(tokens).size(), "no debe haber codigos QR repetidos");
        assertEquals(SESIONES, sessionIds.size(), "cada codigo debe corresponder a una sesion distinta");
    }

    @Test
    @DisplayName("Paso 4: un codigo QR ya canjeado no puede reutilizarse")
    void unCodigoYaCanjeadoNoPuedeReutilizarse() throws Exception {
        var citizen = newCitizen();
        var bin = newSmartBin();
        var session = openSession(bin, 20, 400.0);

        mockMvc.perform(apiPost("/sessions/qr/" + session.getQrToken() + "/confirm")
                        .header("Authorization", citizen.bearer()))
                .andExpect(status().isOk());

        // Intentar reutilizar el codigo QR previamente canjeado: el sistema lo rechaza.
        mockMvc.perform(apiPost("/sessions/qr/" + session.getQrToken() + "/confirm")
                        .header("Authorization", citizen.bearer()))
                .andExpect(status().isConflict());

        // Y tampoco lo admite otro ciudadano distinto.
        var otro = newCitizen();
        mockMvc.perform(apiPost("/sessions/qr/" + session.getQrToken() + "/confirm")
                        .header("Authorization", otro.bearer()))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("US-MN-04 - Reenviar mintWithdrawal tras un fallo de red no duplica el retiro: un solo COMPLETADO")
    void unWithdrawalIdYaProcesadoOnChainNoSeDuplicaAlReenviar() throws Exception {
        var citizen = newCitizen();
        givePoints(citizen, 800);
        String addr = "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed";

        when(contract.mintWithdrawal(any(), any(), any())).thenThrow(new IOException("rpc down"));

        String body = mockMvc.perform(apiPost("/wallet/withdraw")
                        .header("Authorization", citizen.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("toAddress", addr, "points", 800, "mode", "CTC"))))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        Long id = Long.valueOf(field(body, "id", Integer.class));

        // El backend reenvia mintWithdrawal(to, id, amount) (paso 3 del reintento): esta vez
        // el contrato ya lo proceso (el primer envio si llego a minar, solo que la respuesta
        // no llego al backend a tiempo).
        // doThrow(...).when(...), no when(...).thenThrow(...): el mock TODAVIA tiene el stub
        // viejo activo, y evaluar contract.mintWithdrawal(...) dentro de when(...) lo dispararia
        // antes de poder reemplazarlo.
        String selectorWithdrawalAlreadyProcessed =
                Hash.sha3String("WithdrawalAlreadyProcessed(uint256)").substring(0, 10);
        var alreadyProcessed = new ContractRevertException(selectorWithdrawalAlreadyProcessed);
        doThrow(alreadyProcessed).when(contract).mintWithdrawal(any(), any(), any());
        // contract esta mockeado por completo: isWithdrawalAlreadyProcessed tambien hay que
        // stubearlo (sin esto Mockito devuelve false por defecto, no la logica real del selector).
        when(contract.isWithdrawalAlreadyProcessed(alreadyProcessed)).thenReturn(true);

        var request = withdrawalRequestRepository.findById(id).orElseThrow();
        withdrawalCommandService.submit(request);

        var completed = withdrawalRequestRepository.findById(id).orElseThrow();
        assertEquals(WithdrawalStatus.COMPLETADO, completed.getStatus());
        assertEquals("recorded:" + completed.getChainWithdrawalId(), completed.getTxHash());
        assertEquals(1, withdrawalRequestRepository.findByUserIdAndStatus(citizen.id(), WithdrawalStatus.COMPLETADO)
                .size(), "un solo retiro COMPLETADO para este withdrawalId, no dos");
    }

    @Test
    @DisplayName("Un token inexistente no resuelve ninguna sesion")
    void unTokenInexistenteNoResuelveNingunaSesion() throws Exception {
        mockMvc.perform(apiGet("/sessions/qr/00000000000000000000000000000000"))
                .andExpect(status().isNotFound());
    }
}

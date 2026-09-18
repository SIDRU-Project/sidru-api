package com.sidru.sidru_api.cp;

import com.sidru.sidru_api.sessions.application.internal.outboundservices.blockchain.BlockchainPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CP029 — Unicidad del codigo QR generado por sesion.
 * HU: US-17 · Escenario 1 · Prioridad: Alta
 * Criterio de aceptacion: Generacion de un codigo QR unico.
 *
 * Postcondicion: los 50 codigos quedan registrados en el backend con su estado correspondiente.
 */
@DisplayName("CP029 - Unicidad del codigo QR generado por sesion")
class Cp029UnicidadDelCodigoQrTest extends CpBaseTest {

    private static final int SESIONES = 50;

    @MockitoBean
    private BlockchainPort blockchainPort;

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
        when(blockchainPort.recordSession(any())).thenReturn(Optional.empty());

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
    @DisplayName("Un token inexistente no resuelve ninguna sesion")
    void unTokenInexistenteNoResuelveNingunaSesion() throws Exception {
        mockMvc.perform(apiGet("/sessions/qr/00000000000000000000000000000000"))
                .andExpect(status().isNotFound());
    }
}

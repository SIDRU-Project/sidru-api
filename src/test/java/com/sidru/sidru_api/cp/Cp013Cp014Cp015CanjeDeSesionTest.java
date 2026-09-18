package com.sidru.sidru_api.cp;

import com.sidru.sidru_api.sessions.application.internal.outboundservices.blockchain.BlockchainPort;
import com.sidru.sidru_api.sessions.domain.model.valueobjects.SessionStatus;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CP013 / CP014 / CP015 — Canje de la sesion: confirmacion con emision de tokens, rechazo del
 * canje duplicado y rechazo del QR expirado.
 * HU: US-20 (esc. 2), US-23 (esc. 1 y 3) · Prioridad: Alta / Alta / Media
 *
 * <p>El puerto de blockchain se sustituye por un doble de prueba: el CP verifica el contrato
 * del backend (estado CONFIRMED, txHash propagado, saldo acreditado), no la red Amoy. La
 * verificacion del hash en el explorador es el paso manual de CP018/CP030.</p>
 */
@DisplayName("CP013/CP014/CP015 - Canje de sesion")
class Cp013Cp014Cp015CanjeDeSesionTest extends CpBaseTest {

    private static final String TX_HASH =
            "0x5f2c1a0d4e3b6c8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b9c0d1e2f";

    @MockitoBean
    private BlockchainPort blockchainPort;

    // ------------------------------------------------------------------ CP013

    @Test
    @DisplayName("CP013 - Confirmacion: 200, status CONFIRMED, txHash en la respuesta y saldo acreditado")
    void confirmaElCanjeYAcreditaLosTokens() throws Exception {
        when(blockchainPort.recordSession(any())).thenReturn(Optional.of(TX_HASH));

        var citizen = newCitizen();
        var bin = newSmartBin();
        var session = openSession(bin, 25, 500.0);
        int saldoInicial = userProfileRepository.findByUserId(citizen.id()).orElseThrow().getTotalPoints();

        // Paso 1 — POST /sessions/qr/{qrToken}/confirm con el JWT en la cabecera -> HTTP 200.
        // Paso 2 — El cuerpo contiene status CONFIRMED y el txHash de la transaccion.
        mockMvc.perform(apiPost("/sessions/qr/" + session.getQrToken() + "/confirm")
                        .header("Authorization", citizen.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.blockchainTxHash").value(TX_HASH));

        // Paso 3 — El saldo del usuario se incrementa segun los tokens acreditados.
        int saldoFinal = userProfileRepository.findByUserId(citizen.id()).orElseThrow().getTotalPoints();
        assertEquals(saldoInicial + session.getPointsEarned(), saldoFinal);

        // Paso 4 (equivalente automatizable) — el txHash queda persistido para su trazabilidad
        // en el explorador; la consulta en Polygonscan es el paso manual de CP018/CP030.
        var reloaded = sessionRepository.findByQrToken(session.getQrToken()).orElseThrow();
        assertEquals(SessionStatus.CONFIRMED, reloaded.getStatus());
        assertEquals(TX_HASH, reloaded.getBlockchainTxHash());
        assertNotNull(reloaded.getConfirmedAt());
        assertEquals(citizen.id(), reloaded.getUserId());
    }

    // ------------------------------------------------------------------ CP014

    @Test
    @DisplayName("CP014 - Segundo canje de una sesion CONFIRMED: 409 y sin cambios de estado ni saldo")
    void rechazaElCanjeDuplicado() throws Exception {
        when(blockchainPort.recordSession(any())).thenReturn(Optional.of(TX_HASH));

        var citizen = newCitizen();
        var bin = newSmartBin();
        var session = openSession(bin, 25, 500.0);

        mockMvc.perform(apiPost("/sessions/qr/" + session.getQrToken() + "/confirm")
                        .header("Authorization", citizen.bearer()))
                .andExpect(status().isOk());

        int saldoTrasElCanje = userProfileRepository.findByUserId(citizen.id()).orElseThrow().getTotalPoints();

        // Paso 1 — Invocar nuevamente la confirmacion del mismo QR -> HTTP 409.
        // Paso 2 — El mensaje indica que la sesion ya no admite la operacion.
        mockMvc.perform(apiPost("/sessions/qr/" + session.getQrToken() + "/confirm")
                        .header("Authorization", citizen.bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ERR_SES_002"))
                .andExpect(jsonPath("$.message").isNotEmpty());

        // Paso 3 — El estado no se modifico y el saldo no vario.
        var reloaded = sessionRepository.findByQrToken(session.getQrToken()).orElseThrow();
        assertEquals(SessionStatus.CONFIRMED, reloaded.getStatus());
        assertEquals(saldoTrasElCanje,
                userProfileRepository.findByUserId(citizen.id()).orElseThrow().getTotalPoints());
    }

    @Test
    @DisplayName("CP014 - Dos confirmaciones concurrentes del mismo QR: solo una prospera")
    void soloUnaDeDosConfirmacionesConcurrentesProspera() throws Exception {
        when(blockchainPort.recordSession(any())).thenReturn(Optional.of(TX_HASH));

        var citizen = newCitizen();
        var bin = newSmartBin();
        var session = openSession(bin, 25, 500.0);
        int saldoInicial = userProfileRepository.findByUserId(citizen.id()).orElseThrow().getTotalPoints();

        // Paso 4 — Ejecutar dos confirmaciones concurrentes sobre la misma sesion PENDING.
        var barrier = new CyclicBarrier(2);
        Callable<Integer> confirm = () -> {
            barrier.await(10, TimeUnit.SECONDS);   // ambas salen a la vez
            return mockMvc.perform(apiPost("/sessions/qr/" + session.getQrToken() + "/confirm")
                            .header("Authorization", citizen.bearer()))
                    .andReturn().getResponse().getStatus();
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            var futures = pool.invokeAll(List.of(confirm, confirm));
            int first = futures.get(0).get(20, TimeUnit.SECONDS);
            int second = futures.get(1).get(20, TimeUnit.SECONDS);

            // Esperado: solo una prospera; la segunda recibe HTTP 409.
            assertEquals(1, List.of(first, second).stream().filter(s -> s == 200).count(),
                    "exactamente una confirmacion debe responder 200, fueron: " + first + " y " + second);
            assertEquals(1, List.of(first, second).stream().filter(s -> s == 409).count(),
                    "exactamente una confirmacion debe responder 409, fueron: " + first + " y " + second);
        } finally {
            pool.shutdownNow();
        }

        // La sesion conserva un unico registro de canje: el saldo se acredito una sola vez.
        int saldoFinal = userProfileRepository.findByUserId(citizen.id()).orElseThrow().getTotalPoints();
        assertEquals(saldoInicial + session.getPointsEarned(), saldoFinal,
                "el canje concurrente no debe acreditar los puntos dos veces");
    }

    // ------------------------------------------------------------------ CP015

    @Test
    @DisplayName("CP015 - QR fuera de vigencia: 410 con motivo qr_expirado y sesion marcada EXPIRED")
    void rechazaElCanjeDeUnQrExpirado() throws Exception {
        var citizen = newCitizen();
        var bin = newSmartBin();
        var session = openSession(bin, 25, 500.0);

        // Se fuerza el vencimiento de la vigencia del QR.
        session.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        sessionRepository.save(session);

        // Paso 1 — Intentar canjear el codigo QR expirado -> HTTP 410.
        // Paso 2 — El motivo devuelto es "qr_expirado".
        mockMvc.perform(apiPost("/sessions/qr/" + session.getQrToken() + "/confirm")
                        .header("Authorization", citizen.bearer()))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("ERR_SES_003"))
                .andExpect(jsonPath("$.details", Matchers.hasItem("reason: qr_expirado")));

        // Paso 3 — La sesion permanece sin canjear y queda marcada como expirada.
        var reloaded = sessionRepository.findByQrToken(session.getQrToken()).orElseThrow();
        assertEquals(SessionStatus.EXPIRED, reloaded.getStatus(),
                "la sesion debe quedar marcada EXPIRED para que no pueda reutilizarse");
        assertEquals(null, reloaded.getConfirmedAt(), "la sesion no debe figurar como canjeada");

        // Y un segundo intento tampoco prospera.
        mockMvc.perform(apiPost("/sessions/qr/" + session.getQrToken() + "/confirm")
                        .header("Authorization", citizen.bearer()))
                .andExpect(status().isConflict());
    }
}

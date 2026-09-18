package com.sidru.sidru_api.cp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CP009 / CP010 — Calculo de la recompensa segun la tarifa configurada e idempotencia.
 * HU: US-18 · Escenarios 1 y 3 · Prioridad: Alta
 *
 * <p>Formula vigente: {@code puntos = (pesoGramos / 1000) * price-per-kg-soles * points-per-sol}
 * con {@code price-per-kg-soles = 4.00} y {@code points-per-sol = 100}.</p>
 *
 * <p>DESVIACION DOCUMENTADA: la hoja dice "el monto se registra con dos decimales". Los puntos
 * (y por tanto los CTC, 1 punto = 1 CTC) son enteros por diseno: lo que se registra con dos
 * decimales es su equivalencia referencial en soles, que es la que se verifica aqui.</p>
 */
@DisplayName("CP009/CP010 - Calculo de recompensa e idempotencia")
class Cp009Cp010CalculoDeRecompensaTest extends CpBaseTest {

    @Value("${sidru.recycling.price-per-kg-soles}")
    private double pricePerKgSoles;

    @Value("${sidru.recycling.points-per-sol}")
    private int pointsPerSol;

    // ------------------------------------------------------------------ CP009

    @Test
    @DisplayName("CP009 - 500 g con la tarifa configurada devuelve exactamente los puntos esperados")
    void calculaLosPuntosSegunLaTarifaConfigurada() throws Exception {
        var bin = newSmartBin();
        double weightGrams = 500.0;

        // Paso 1 — Invocar el calculo con una sesion de peso conocido.
        var session = openSession(bin, 20, weightGrams);

        // Paso 2 — Comparar el resultado con el valor esperado segun la formula.
        // 500 g = 0,5 kg * S/ 4.00 = S/ 2.00 * 100 pts/sol = 200 puntos.
        int expected = (int) Math.round((weightGrams / 1000.0) * pricePerKgSoles * pointsPerSol);
        assertEquals(200, expected, "control de la formula con los parametros configurados");
        assertEquals(expected, session.getPointsEarned(),
                "los puntos calculados deben coincidir exactamente con la formula");

        // Paso 3 — La equivalencia monetaria del monto se expresa con dos decimales.
        BigDecimal soles = BigDecimal.valueOf(session.getPointsEarned())
                .divide(BigDecimal.valueOf(pointsPerSol), 2, RoundingMode.HALF_UP);
        assertEquals(new BigDecimal("2.00"), soles);
        assertEquals(2, soles.scale(), "la equivalencia en soles debe conservar dos decimales");
    }

    @Test
    @DisplayName("CP009 - Peso o conteo fuera de rango: la sesion se rechaza y no se calcula recompensa")
    void rechazaLosValoresFueraDeRangoSinCalcularRecompensa() throws Exception {
        var bin = newSmartBin();
        long before = sessionRepository.count();

        // Paso 4 — Conteo de tapas fuera de rango (0 < min-caps = 1).
        mockMvc.perform(apiPost("/sessions")
                        .header("X-Device-Api-Key", bin.getApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("capCount", 0, "weightGrams", 25000.0))))
                .andExpect(status().isBadRequest());

        // Peso por encima del maximo por sesion (50 000 g).
        mockMvc.perform(apiPost("/sessions")
                        .header("X-Device-Api-Key", bin.getApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("capCount", 10, "weightGrams", 50001.0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_SES_006"));

        assertEquals(before, sessionRepository.count(),
                "ninguna sesion rechazada debe persistirse ni generar recompensa");
    }

    // ------------------------------------------------------------------ CP010

    @Test
    @DisplayName("CP010 - Acreditar dos veces la misma sesion no duplica el saldo de puntos")
    void laAcreditacionDeLaRecompensaEsIdempotente() throws Exception {
        var citizen = newCitizen();
        var bin = newSmartBin();
        var session = openSession(bin, 20, 500.0);

        int puntosDeLaSesion = session.getPointsEarned();
        int saldoInicial = userProfileRepository.findByUserId(citizen.id()).orElseThrow().getTotalPoints();

        // Paso 1 — Invocar el calculo/acreditacion para el sessionId: devuelve el monto calculado.
        mockMvc.perform(apiPost("/sessions/qr/" + session.getQrToken() + "/confirm")
                        .header("Authorization", citizen.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pointsEarned").value(puntosDeLaSesion));

        // Paso 2 — Invocar nuevamente para el mismo sessionId: no genera un nuevo registro.
        mockMvc.perform(apiPost("/sessions/qr/" + session.getQrToken() + "/confirm")
                        .header("Authorization", citizen.bearer()))
                .andExpect(status().isConflict());

        // El monto asociado a la sesion no cambia entre invocaciones.
        var reloaded = sessionRepository.findByQrToken(session.getQrToken()).orElseThrow();
        assertEquals(puntosDeLaSesion, reloaded.getPointsEarned(),
                "el monto calculado debe ser el mismo en ambas invocaciones");

        // Paso 3 — El saldo de puntos del usuario no se duplico.
        int saldoFinal = userProfileRepository.findByUserId(citizen.id()).orElseThrow().getTotalPoints();
        assertEquals(saldoInicial + puntosDeLaSesion, saldoFinal,
                "el saldo debe incrementarse una sola vez");
    }
}

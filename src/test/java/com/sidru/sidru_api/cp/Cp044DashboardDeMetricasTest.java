package com.sidru.sidru_api.cp;

import com.sidru.sidru_api.iam.domain.model.valueobjects.Roles;
import com.sidru.sidru_api.sessions.application.internal.outboundservices.blockchain.BlockchainPort;
import com.sidru.sidru_api.sessions.domain.model.aggregates.RecyclingSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CP044 — Dashboard de metricas operativas y exportacion.
 * HU: US-36 · Escenario 1 · Prioridad: Baja
 * Criterio de aceptacion: Visualizacion de metricas operativas.
 *
 * Postcondicion: el archivo exportado queda disponible para su revision.
 */
@DisplayName("CP044 - Dashboard de metricas operativas y exportacion")
class Cp044DashboardDeMetricasTest extends CpBaseTest {

    @MockitoBean
    private BlockchainPort blockchainPort;

    /** Siembra una sesion confirmada del ciudadano en el bin indicado. */
    private RecyclingSession confirmedSession(Long userId, Long binId, int caps, double grams, int points) {
        var session = new RecyclingSession(binId, caps, grams, points, LocalDateTime.now().plusMinutes(15));
        session.confirm(userId);
        return sessionRepository.save(session);
    }

    @Test
    @DisplayName("Paso 1-2: el dashboard muestra los indicadores operativos del sistema")
    void muestraLosIndicadoresOperativos() throws Exception {
        var admin = newUser(Roles.ROLE_ADMIN.name());
        var citizen = newCitizen();
        var bin = newSmartBin();
        confirmedSession(citizen.id(), bin.getId(), 25, 500.0, 200);

        // Paso 1 — Acceder al dashboard con una cuenta de administrador.
        // Paso 2 — Verificar los indicadores presentados.
        mockMvc.perform(apiGet("/metrics").header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSessions").isNumber())          // total de sesiones por periodo
                .andExpect(jsonPath("$.confirmedSessions").isNumber())
                .andExpect(jsonPath("$.weightKg").isNumber())               // peso reciclado
                .andExpect(jsonPath("$.ctcMinted").isNumber())              // tokens emitidos
                .andExpect(jsonPath("$.registeredUsers").isNumber())
                .andExpect(jsonPath("$.activeUsers").isNumber())            // usuarios activos
                .andExpect(jsonPath("$.activeBins").isNumber())
                .andExpect(jsonPath("$.topBins").isArray())                 // ranking de dispositivos
                .andExpect(jsonPath("$.impact.co2AvoidedKg").isNumber())
                .andExpect(jsonPath("$.impact.energySavedKwh").isNumber());
    }

    @Test
    @DisplayName("Paso 3: al filtrar por rango de fechas los valores se recalculan")
    void recalculaLosValoresSegunElRangoDeFechas() throws Exception {
        var admin = newUser(Roles.ROLE_ADMIN.name());
        var citizen = newCitizen();
        var bin = newSmartBin();
        confirmedSession(citizen.id(), bin.getId(), 25, 500.0, 200);

        LocalDate hoy = LocalDate.now();
        LocalDate haceUnMes = hoy.minusMonths(1);

        // Rango que incluye hoy: la sesion sembrada cuenta.
        String conDatos = mockMvc.perform(apiGet("/metrics?from=" + haceUnMes + "&to=" + hoy)
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value(haceUnMes.toString()))
                .andExpect(jsonPath("$.to").value(hoy.toString()))
                .andExpect(jsonPath("$.confirmedSessions",
                        org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.topBins",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.empty())))
                .andReturn().getResponse().getContentAsString();

        // Rango antiguo, sin actividad: los mismos indicadores dan cero.
        String sinDatos = mockMvc.perform(apiGet("/metrics?from=2020-01-01&to=2020-01-31")
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSessions").value(0))
                .andExpect(jsonPath("$.confirmedSessions").value(0))
                .andExpect(jsonPath("$.activeUsers").value(0))
                .andExpect(jsonPath("$.topBins").isEmpty())
                .andReturn().getResponse().getContentAsString();

        assertNotEquals(conDatos, sinDatos, "el filtro por fechas debe cambiar el resultado");
    }

    @Test
    @DisplayName("Paso 4: la exportacion a CSV descarga el conjunto filtrado")
    void exportaACsvElConjuntoFiltrado() throws Exception {
        var admin = newUser(Roles.ROLE_ADMIN.name());
        var citizen = newCitizen();
        var bin = newSmartBin();
        confirmedSession(citizen.id(), bin.getId(), 25, 500.0, 200);

        LocalDate hoy = LocalDate.now();
        LocalDate haceUnMes = hoy.minusMonths(1);

        String csv = mockMvc.perform(apiGet("/metrics/export.csv?from=" + haceUnMes + "&to=" + hoy)
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("sidru-metricas.csv")))
                .andReturn().getResponse().getContentAsString();

        // El archivo trae los indicadores y el ranking de dispositivos del rango filtrado.
        assertTrue(csv.startsWith("metrica,valor"), "el CSV debe abrir con la cabecera de indicadores");
        assertTrue(csv.contains("desde," + haceUnMes), "el CSV debe reflejar el filtro aplicado");
        assertTrue(csv.contains("hasta," + hoy));
        assertTrue(csv.contains("sesiones_confirmadas,"));
        assertTrue(csv.contains("tapas_recicladas,"));
        assertTrue(csv.contains("ctc_emitidos,"));
        assertTrue(csv.contains("usuarios_activos,"));
        assertTrue(csv.contains("smart_bin_id,device_code,sesiones,tapas,peso_kg"),
                "el CSV debe incluir la tabla del ranking de dispositivos");
        assertTrue(csv.contains(bin.getDeviceCode()),
                "el ranking debe nombrar el Smart Bin con actividad en el rango");
    }

    @Test
    @DisplayName("Paso 5: registrar una nueva sesion actualiza las metricas")
    void unaNuevaSesionActualizaLasMetricas() throws Exception {
        when(blockchainPort.recordSession(any())).thenReturn(Optional.empty());

        var admin = newUser(Roles.ROLE_ADMIN.name());
        var citizen = newCitizen();
        var bin = newSmartBin();

        int confirmadasAntes = Integer.parseInt(field(
                mockMvc.perform(apiGet("/metrics").header("Authorization", admin.bearer()))
                        .andReturn().getResponse().getContentAsString(),
                "confirmedSessions", Integer.class).toString());

        var session = openSession(bin, 15, 300.0);
        mockMvc.perform(apiPost("/sessions/qr/" + session.getQrToken() + "/confirm")
                        .header("Authorization", citizen.bearer()))
                .andExpect(status().isOk());

        mockMvc.perform(apiGet("/metrics").header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmedSessions").value(confirmadasAntes + 1));
    }

    @Test
    @DisplayName("La exportacion es exclusiva de administradores")
    void laExportacionEsExclusivaDeAdministradores() throws Exception {
        var citizen = newCitizen();

        mockMvc.perform(apiGet("/metrics/export.csv").header("Authorization", citizen.bearer()))
                .andExpect(status().isForbidden());

        mockMvc.perform(apiGet("/metrics/export.csv"))
                .andExpect(status().isUnauthorized());

        // El dashboard de lectura si esta disponible para cualquier usuario autenticado
        // (alimenta la pantalla de impacto de la app).
        mockMvc.perform(apiGet("/metrics").header("Authorization", citizen.bearer()))
                .andExpect(status().isOk());
    }
}

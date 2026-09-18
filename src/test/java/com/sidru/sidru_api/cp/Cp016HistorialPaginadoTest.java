package com.sidru.sidru_api.cp;

import com.fasterxml.jackson.databind.JsonNode;
import com.sidru.sidru_api.sessions.domain.model.aggregates.RecyclingSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CP016 — Consulta paginada del historial de reciclaje.
 * HU: US-22 · Escenario 1 · Prioridad: Media
 * Criterio de aceptacion: Consulta paginada del historial.
 *
 * Postcondicion: no se modifica ningun registro del sistema.
 */
@DisplayName("CP016 - Consulta paginada del historial de reciclaje")
class Cp016HistorialPaginadoTest extends CpBaseTest {

    /** Umbral de tiempo de respuesta exigido por el caso de prueba. */
    private static final long MAX_RESPONSE_MILLIS = 1_000L;

    /**
     * Siembra sesiones confirmadas del ciudadano directamente en persistencia. Se evita el
     * camino HTTP a proposito: el objeto de este CP es la consulta, no el alta.
     */
    private void seedConfirmedSessions(Long userId, Long smartBinId, int amount) {
        List<RecyclingSession> batch = new ArrayList<>(amount);
        for (int i = 0; i < amount; i++) {
            var session = new RecyclingSession(
                    smartBinId, 10 + i, 50.0 + i, 20 + i, LocalDateTime.now().plusMinutes(15));
            session.confirm(userId);
            batch.add(sessionRepository.save(session));
        }
    }

    @Test
    @DisplayName("Paso 1-3: pagina de 20, orden descendente por fecha y campos expuestos")
    void devuelveLaPaginaOrdenadaConLosCamposEsperados() throws Exception {
        var citizen = newCitizen();
        var bin = newSmartBin();
        seedConfirmedSessions(citizen.id(), bin.getId(), 25);

        // Paso 1 — GET /api/v1/sessions/me?page=0&size=20 con el JWT valido -> 200 con lista paginada.
        String body = mockMvc.perform(apiGet("/sessions/me?page=0&size=20")
                        .header("Authorization", citizen.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(25))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andReturn().getResponse().getContentAsString();

        JsonNode content = objectMapper.readTree(body).get("content");
        assertEquals(20, content.size(), "la pagina debe traer exactamente 20 elementos");

        // Paso 2 — Los resultados estan ordenados por fecha de registro en orden descendente.
        LocalDateTime previous = null;
        for (JsonNode item : content) {
            LocalDateTime current = LocalDateTime.parse(item.get("createdAt").asText());
            if (previous != null) {
                assertFalse(current.isAfter(previous),
                        "el historial debe venir de la fecha mas reciente a la mas antigua");
            }
            previous = current;
        }

        // Paso 3 — Cada elemento expone sessionId, fecha, peso, tapas, tokens y estado.
        JsonNode first = content.get(0);
        for (String requiredField : List.of(
                "id", "createdAt", "weightGrams", "capCount", "pointsEarned", "status")) {
            assertTrue(first.has(requiredField) && !first.get(requiredField).isNull(),
                    "cada elemento del historial debe exponer el campo " + requiredField);
        }

        // La segunda pagina completa el conjunto.
        mockMvc.perform(apiGet("/sessions/me?page=1&size=20")
                        .header("Authorization", citizen.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(5))
                .andExpect(jsonPath("$.page").value(1));
    }

    @Test
    @DisplayName("Paso 4: con 200 sesiones el tiempo de respuesta se mantiene por debajo de 1 s")
    void respondeEnMenosDeUnSegundoConDoscientasSesiones() throws Exception {
        var citizen = newCitizen();
        var bin = newSmartBin();
        seedConfirmedSessions(citizen.id(), bin.getId(), 200);

        // Calentamiento: la primera peticion paga la inicializacion perezosa de JPA/Jackson,
        // que no forma parte del tiempo de respuesta en regimen que mide el CP.
        mockMvc.perform(apiGet("/sessions/me?page=0&size=20").header("Authorization", citizen.bearer()))
                .andExpect(status().isOk());

        long start = System.nanoTime();
        mockMvc.perform(apiGet("/sessions/me?page=0&size=20").header("Authorization", citizen.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(200));
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;

        assertTrue(elapsedMillis < MAX_RESPONSE_MILLIS,
                "el historial debe responder en menos de " + MAX_RESPONSE_MILLIS
                        + " ms, tardo " + elapsedMillis + " ms");
    }

    @Test
    @DisplayName("Paso 5: usuario sin sesiones registradas devuelve lista vacia y totalElements en cero")
    void usuarioSinSesionesDevuelveListaVacia() throws Exception {
        var citizen = newCitizen();

        mockMvc.perform(apiGet("/sessions/me?page=0&size=20").header("Authorization", citizen.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    @DisplayName("Compatibilidad: sin parametros de paginacion se conserva la lista simple")
    void sinParametrosDevuelveLaListaCompletaComoAntes() throws Exception {
        var citizen = newCitizen();
        var bin = newSmartBin();
        seedConfirmedSessions(citizen.id(), bin.getId(), 3);

        mockMvc.perform(apiGet("/sessions/me").header("Authorization", citizen.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(3));
    }
}

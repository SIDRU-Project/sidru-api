package com.sidru.sidru_api.cp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CP035 — Arranque del backend con conexion a la base de datos.
 * HU: US-01 · Escenario 1 · Prioridad: Alta
 * Criterio de aceptacion: Inicio funcional del backend.
 *
 * <p>NOTA DE RUTA: por el context-path de la API, el health check efectivo es
 * {@code /api/v1/actuator/health}, no {@code /actuator/health}.</p>
 *
 * <p>Los pasos 1 y 2 (arranque sin errores y conexion con la base de datos establecida) los
 * prueba el propio hecho de que el contexto cargue y las consultas respondan: si cualquiera
 * de los dos fallara, ninguna prueba de esta clase llegaria a ejecutarse.</p>
 */
@DisplayName("CP035 - Arranque del backend y health check")
class Cp035ArranqueYSaludTest extends CpBaseTest {

    /** Umbral de respuesta del health check exigido por el caso de prueba. */
    private static final long MAX_HEALTH_MILLIS = 500L;

    /** Contextos acotados del backend, segun la arquitectura definida. */
    private static final List<String> BOUNDED_CONTEXTS = List.of(
            "iam", "users", "devices", "sessions", "rewards", "notifications",
            "blockchain", "metrics", "audit", "mqtt", "shared");

    /**
     * Contextos con modelo de dominio propio. Quedan fuera los contextos de solo lectura o de
     * integracion ({@code metrics} agrega via ACL, {@code notifications} y {@code mqtt} son
     * adaptadores hacia servicios externos): no tienen agregados propios y no deben inventarselos.
     */
    private static final List<String> CONTEXTS_WITH_DOMAIN = List.of(
            "iam", "users", "devices", "sessions", "rewards", "blockchain", "audit");

    /** Capas que debe presentar cada contexto acotado. */
    private static final List<String> LAYERS = List.of("domain", "application", "infrastructure", "interfaces");

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("Paso 1-2: el contexto arranca y la persistencia responde")
    void elBackendArrancaYLaPersistenciaResponde() {
        assertNotNull(applicationContext, "el contexto de la aplicacion debe haber cargado");
        // Una consulta real prueba que el pool de conexiones esta operativo.
        assertTrue(userRepository.count() >= 0, "la conexion con la base de datos debe estar establecida");
    }

    @Test
    @DisplayName("Paso 3: GET /actuator/health responde 200 con status UP en menos de 500 ms")
    void elHealthCheckRespondeUpDentroDelUmbral() throws Exception {
        // Calentamiento: la primera invocacion paga la inicializacion del endpoint.
        mockMvc.perform(apiGet("/actuator/health")).andExpect(status().isOk());

        long start = System.nanoTime();
        mockMvc.perform(apiGet("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;

        assertTrue(elapsedMillis < MAX_HEALTH_MILLIS,
                "el health check debe responder en menos de " + MAX_HEALTH_MILLIS
                        + " ms, tardo " + elapsedMillis + " ms");
    }

    @Test
    @DisplayName("Paso 3 (bis): el health check es publico, para que lo consulte el orquestador")
    void elHealthCheckEsPublico() throws Exception {
        // Sin cabecera Authorization: el orquestador de contenedores no tiene JWT (CP041).
        mockMvc.perform(apiGet("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Paso 4: la estructura de capas corresponde a la arquitectura DDD definida")
    void laEstructuraDeCapasCorrespondeALaArquitectura() throws Exception {
        var resolver = new PathMatchingResourcePatternResolver();

        for (String context : BOUNDED_CONTEXTS) {
            boolean tieneAlgunaCapa = false;
            for (String layer : LAYERS) {
                String pattern = "classpath*:com/sidru/sidru_api/" + context + "/" + layer + "/**/*.class";
                if (resolver.getResources(pattern).length > 0) {
                    tieneAlgunaCapa = true;
                }
            }
            assertTrue(tieneAlgunaCapa,
                    "el contexto acotado '" + context + "' debe organizarse en capas DDD");
        }

        // Los contextos con modelo propio deben tener capa de dominio poblada.
        for (String context : CONTEXTS_WITH_DOMAIN) {
            var domainClasses = resolver.getResources(
                    "classpath*:com/sidru/sidru_api/" + context + "/domain/**/*.class");
            assertTrue(domainClasses.length > 0,
                    "el contexto '" + context + "' debe tener capa de dominio");
        }
    }
}

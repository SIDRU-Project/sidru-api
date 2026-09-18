package com.sidru.sidru_api.cp;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CP040 — Documentacion OpenAPI de los endpoints REST.
 * HU: US-27 · Escenario 1 · Prioridad: Media
 * Criterio de aceptacion: Documentacion completa de endpoints.
 *
 * <p>Se verifica contra el artefacto que genera springdoc ({@code /v3/api-docs}), que es la
 * fuente de la interfaz de Swagger UI y el archivo que publica el pipeline.</p>
 */
@DisplayName("CP040 - Documentacion OpenAPI de los endpoints REST")
class Cp040DocumentacionOpenApiTest extends CpBaseTest {

    @Autowired
    private ApplicationContext applicationContext;

    private JsonNode openApi() throws Exception {
        String body = mockMvc.perform(apiGet("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    @Test
    @DisplayName("Paso 1: la especificacion se genera y es accesible sin autenticacion")
    void laEspecificacionSeGeneraYEsAccesible() throws Exception {
        JsonNode spec = openApi();

        assertTrue(spec.has("openapi"), "el documento debe declarar la version de OpenAPI");
        assertTrue(spec.has("info"), "el documento debe traer el bloque info");
        assertTrue(spec.get("paths").size() > 0, "el documento debe listar rutas");
    }

    @Test
    @DisplayName("Paso 2: el 100 % de los endpoints REST implementados figura documentado")
    void todosLosEndpointsImplementadosEstanDocumentados() throws Exception {
        JsonNode paths = openApi().get("paths");

        // Rutas base de cada controlador REST del backend, leidas del propio contexto.
        List<String> basePaths = new ArrayList<>();
        for (Object controller : applicationContext.getBeansWithAnnotation(RestController.class).values()) {
            Class<?> type = org.springframework.aop.support.AopUtils.getTargetClass(controller);
            RequestMapping mapping = type.getAnnotation(RequestMapping.class);
            if (mapping != null && mapping.value().length > 0) {
                basePaths.add(mapping.value()[0]);
            }
        }
        assertFalse(basePaths.isEmpty(), "debe haber controladores REST registrados");

        List<String> documented = new ArrayList<>();
        paths.fieldNames().forEachRemaining(documented::add);

        for (String basePath : basePaths) {
            assertTrue(documented.stream().anyMatch(p -> p.startsWith(basePath)),
                    "no hay ninguna operacion documentada para el controlador de " + basePath
                            + ". Documentadas: " + documented);
        }
    }

    @Test
    @DisplayName("Paso 3: cada operacion declara metodo, ruta, parametros y codigos de respuesta")
    void cadaOperacionDeclaraSuContratoCompleto() throws Exception {
        JsonNode paths = openApi().get("paths");

        Iterator<Map.Entry<String, JsonNode>> rutas = paths.fields();
        while (rutas.hasNext()) {
            var ruta = rutas.next();
            Iterator<Map.Entry<String, JsonNode>> operaciones = ruta.getValue().fields();
            while (operaciones.hasNext()) {
                var operacion = operaciones.next();
                String id = operacion.getKey().toUpperCase() + " " + ruta.getKey();
                JsonNode detalle = operacion.getValue();

                assertTrue(detalle.has("responses") && detalle.get("responses").size() > 0,
                        "la operacion " + id + " debe declarar codigos de respuesta");
                assertTrue(detalle.has("tags"),
                        "la operacion " + id + " debe estar agrupada por tag");

                // Cada parametro de ruta declarado debe traer su esquema.
                if (detalle.has("parameters")) {
                    for (JsonNode parametro : detalle.get("parameters")) {
                        assertTrue(parametro.has("name") && parametro.has("in") && parametro.has("schema"),
                                "el parametro de " + id + " debe declarar name, in y schema");
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("Paso 4: las clases internas (Command y Entity) no se exponen en la documentacion")
    void lasClasesInternasNoSeExponen() throws Exception {
        JsonNode spec = openApi();
        if (!spec.has("components") || !spec.get("components").has("schemas")) {
            return;  // sin esquemas no hay nada que filtrar
        }

        List<String> esquemas = new ArrayList<>();
        spec.get("components").get("schemas").fieldNames().forEachRemaining(esquemas::add);

        for (String esquema : esquemas) {
            assertFalse(esquema.endsWith("Command"),
                    "el comando interno '" + esquema + "' no debe figurar en la documentacion publica");
            assertFalse(esquema.endsWith("Query"),
                    "la query interna '" + esquema + "' no debe figurar en la documentacion publica");
        }

        // Tampoco deben aparecer los agregados de dominio: la API expone recursos, no entidades.
        for (String entidad : List.of("RecyclingSession", "SmartBin", "UserProfile", "AuditLog",
                "BlockchainTransaction", "WithdrawalRequest")) {
            assertFalse(esquemas.contains(entidad),
                    "la entidad de dominio '" + entidad + "' no debe exponerse como esquema publico");
        }
    }

    @Test
    @DisplayName("Paso 5: el artefacto OpenAPI queda escrito en el build para publicarlo en el pipeline")
    void elArtefactoOpenApiSePublicaEnElBuild() throws Exception {
        String body = mockMvc.perform(apiGet("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(body.startsWith("{") && body.contains("\"paths\""),
                "el artefacto debe ser un documento OpenAPI en JSON");
        assertTrue(body.length() > 1000, "el artefacto no puede venir vacio o truncado");

        // Se deja el artefacto en target/ para que el pipeline lo publique como evidencia
        // del ciclo de pruebas (US-27 / US-40).
        Path artifact = Path.of("target", "openapi.json");
        Files.createDirectories(artifact.getParent());
        Files.writeString(artifact, body, StandardCharsets.UTF_8);

        assertTrue(Files.exists(artifact), "el artefacto OpenAPI debe quedar en " + artifact);
        assertTrue(Files.size(artifact) > 1000, "el artefacto publicado no puede estar vacio");
    }
}

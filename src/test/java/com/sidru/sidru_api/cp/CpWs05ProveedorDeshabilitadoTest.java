package com.sidru.sidru_api.cp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CP-WS-05 — Proveedor deshabilitado (GOOGLE_CLIENT_IDS vacio). Contexto propio con Google sin client IDs. */
@DisplayName("CP-WS-05 - Proveedor deshabilitado")
@TestPropertySource(properties = {"sidru.auth.google.client-ids=", "sidru.auth.apple.client-ids="})
class CpWs05ProveedorDeshabilitadoTest extends CpWsBase {

    @Test
    @DisplayName("CP-WS-05: /providers no incluye google ni apple y los POST responden 503 sin crear usuarios")
    void deshabilitadoDa503() throws Exception {
        long before = userRepository.count();

        mockMvc.perform(apiGet("/authentication/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password").value(true))
                .andExpect(jsonPath("$.google").doesNotExist())
                .andExpect(jsonPath("$.apple").doesNotExist());

        google(googleToken(uniqueSub(), uniqueEmail("off"), true, "Apagado"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ERR_AUTH_PROVIDER_DISABLED"));
        apple(appleToken(uniqueSub(), uniqueEmail("off"), true), null)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ERR_AUTH_PROVIDER_DISABLED"));

        assertEquals(before, userRepository.count());
    }
}

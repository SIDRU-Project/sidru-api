package com.sidru.sidru_api.cp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CP-WS-06 y 07 — Entrar con Apple (US-WS-05). El backend implementa el verificador aunque la
 * app aun no ofrezca el boton (Fase 4b): aqui se prueba con el verificador falso.
 */
@DisplayName("CP-WS-06,07 - Login con Apple")
class CpWs06Cp07AppleTest extends CpWsBase {

    @Test
    @DisplayName("CP-WS-06: correo relay de Apple crea una cuenta nueva aunque exista otra cuenta del ciudadano")
    void correoRelayCreaCuentaNueva() throws Exception {
        var existing = newCitizen();
        String relay = "x7k2" + uniqueSub().substring(4, 12) + "@privaterelay.appleid.com";

        var body = bodyOf(apple(appleToken(uniqueSub(), relay, true), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newUser").value(true))
                .andExpect(jsonPath("$.email").value(relay)));

        assertNotEquals(existing.id().longValue(), idOf(body));
        // La cuenta original no se toca.
        assertEquals(existing.email(), userRepository.findById(existing.id()).orElseThrow().getEmail());
    }

    @Test
    @DisplayName("CP-WS-07: fullName del request nombra el perfil; sin fullName usa la parte local del correo")
    void nombreDelPerfil() throws Exception {
        String conNombre = "ana." + uniqueSub().substring(4, 10) + "@privaterelay.appleid.com";
        long idConNombre = idOf(bodyOf(apple(appleToken(uniqueSub(), conNombre, true), "Ana Ríos")
                .andExpect(status().isOk())));
        assertEquals("Ana Ríos", userProfileRepository.findByUserId(idConNombre).orElseThrow().getFullName());

        String local = "sin" + uniqueSub().substring(4, 10);
        long idSinNombre = idOf(bodyOf(apple(appleToken(uniqueSub(), local + "@privaterelay.appleid.com", true), null)
                .andExpect(status().isOk())));
        assertEquals(local, userProfileRepository.findByUserId(idSinNombre).orElseThrow().getFullName());
    }

    @Test
    @DisplayName("Apple tambien vincula por correo verificado y rechaza tokens invalidos con 401")
    void vinculaYRechaza() throws Exception {
        var existing = newCitizen();
        apple(appleToken(uniqueSub(), existing.email(), true), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(existing.id()))
                .andExpect(jsonPath("$.newUser").value(false));

        apple("basura", null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERR_AUTH_SOCIAL_INVALID_TOKEN"));
    }
}

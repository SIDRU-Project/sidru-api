package com.sidru.sidru_api.cp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CP-WS-12 — nonce del flujo web por redirección (design 3.3): liga el ID token a la sesión del navegador. */
@DisplayName("CP-WS-12 - Nonce en el acceso con Google")
class CpWs12NonceTest extends CpWsBase {

    @Test
    @DisplayName("nonce correcto -> 200 y cuenta nueva")
    void nonceCorrecto() throws Exception {
        String nonce = "n-" + uniqueSub();
        googleWithNonce(googleTokenWithNonce(uniqueSub(), uniqueEmail("nonce-ok"), nonce), nonce)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newUser").value(true));
    }

    @Test
    @DisplayName("nonce distinto -> 401 ERR_AUTH_SOCIAL_INVALID_TOKEN y no se crea nada")
    void nonceDistinto() throws Exception {
        long users = userRepository.count();
        long identities = socialIdentityRepository.count();

        googleWithNonce(googleTokenWithNonce(uniqueSub(), uniqueEmail("nonce-mal"), "el-del-token"), "el-de-la-solicitud")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERR_AUTH_SOCIAL_INVALID_TOKEN"))
                .andExpect(jsonPath("$.token").doesNotExist());

        assertEquals(users, userRepository.count());
        assertEquals(identities, socialIdentityRepository.count());
    }

    @Test
    @DisplayName("el request trae nonce y el token no -> 401 sin crear nada")
    void tokenSinNonce() throws Exception {
        long users = userRepository.count();

        googleWithNonce(googleToken(uniqueSub(), uniqueEmail("sin-nonce"), true, "Sin Nonce"), "uno")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERR_AUTH_SOCIAL_INVALID_TOKEN"));

        assertEquals(users, userRepository.count());
    }

    @Test
    @DisplayName("sin nonce en el request -> como hoy (200), aunque el token lo traiga")
    void sinNonceEnElRequest() throws Exception {
        google(googleToken(uniqueSub(), uniqueEmail("compat"), true, "Compat")).andExpect(status().isOk());
        google(googleTokenWithNonce(uniqueSub(), uniqueEmail("compat2"), "x")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("nonce de más de 128 caracteres -> 400")
    void nonceMuyLargo() throws Exception {
        String largo = "n".repeat(129);
        googleWithNonce(googleTokenWithNonce(uniqueSub(), uniqueEmail("largo"), largo), largo)
                .andExpect(status().isBadRequest());
    }
}

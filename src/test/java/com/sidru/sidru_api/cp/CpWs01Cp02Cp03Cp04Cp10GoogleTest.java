package com.sidru.sidru_api.cp;

import com.sidru.sidru_api.iam.domain.model.valueobjects.SocialProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CP-WS-01 a 04 y 10 — Entrar con Google (US-WS-04, US-WS-06). Verificador falso: sin red.
 */
@DisplayName("CP-WS-01..04,10 - Login con Google")
class CpWs01Cp02Cp03Cp04Cp10GoogleTest extends CpWsBase {

    @Test
    @DisplayName("CP-WS-01: primera vez crea un CITIZEN con perfil (nombre de Google, 0 puntos, sin telefono ni distrito)")
    void primeraVezCreaCiudadano() throws Exception {
        String email = uniqueEmail("ana.rios");
        var result = google(googleToken(uniqueSub(), email, true, "Ana Ríos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newUser").value(true))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.token").isNotEmpty());
        var body = bodyOf(result);

        var user = userRepository.findByEmail(email).orElseThrow();
        assertEquals(idOf(body), user.getId());
        assertEquals(java.util.List.of("ROLE_CITIZEN"),
                user.getRoles().stream().map(r -> r.getStringName()).toList());

        var profile = userProfileRepository.findByUserId(user.getId()).orElseThrow();
        assertEquals("Ana Ríos", profile.getFullName());
        assertEquals(0, profile.getTotalPoints());
        assertNull(profile.getPhone());
        assertNull(profile.getDistrict());

        // El JWT de SIDRU emitido sirve para el resto de la API.
        mockMvc.perform(apiGet("/profiles/me").header("Authorization", "Bearer " + body.get("token")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CP-WS-02: segundo acceso devuelve el MISMO usuario con newUser = false")
    void segundoAccesoMismoUsuario() throws Exception {
        String sub = uniqueSub();
        String email = uniqueEmail("segundo");
        long firstId = idOf(bodyOf(google(googleToken(sub, email, true, "Segundo Acceso"))
                .andExpect(status().isOk())));

        var second = bodyOf(google(googleToken(sub, email, true, "Segundo Acceso"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newUser").value(false)));
        assertEquals(firstId, idOf(second));
        assertEquals(1, userRepository.findAll().stream().filter(u -> email.equals(u.getEmail())).count());
    }

    @Test
    @DisplayName("CP-WS-03: cuenta con contrasena se vincula por correo verificado y la contrasena sigue funcionando")
    void vinculaCuentaExistente() throws Exception {
        var existing = newCitizen();
        var body = bodyOf(google(googleToken(uniqueSub(), existing.email(), true, "Luis P"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newUser").value(false)));
        assertEquals(existing.id().longValue(), idOf(body));

        var identities = socialIdentityRepository.findAll().stream()
                .filter(i -> i.getUserId().equals(existing.id())).toList();
        assertEquals(1, identities.size());
        assertEquals(SocialProvider.GOOGLE, identities.get(0).getProvider());

        // La contrasena original sigue abriendo la cuenta.
        assertNotNull(signIn(existing.email()));
    }

    @Test
    @DisplayName("CP-WS-04: firma/formato invalido -> 401 ERR_AUTH_SOCIAL_INVALID_TOKEN y no se crea usuario")
    void tokenInvalidoNoCreaUsuario() throws Exception {
        long before = userRepository.count();
        google("esto-no-es-un-token")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERR_AUTH_SOCIAL_INVALID_TOKEN"));
        // Token con proveedor equivocado (Apple presentado a /google): tambien se rechaza.
        google(appleToken(uniqueSub(), uniqueEmail("otro"), true))
                .andExpect(status().isUnauthorized());
        assertEquals(before, userRepository.count());
    }

    @Test
    @DisplayName("CP-WS-04: idToken vacio o enorme -> 400 ERR_INVALID_001")
    void validacionDelCuerpo() throws Exception {
        mockMvc.perform(apiPost("/authentication/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("idToken", ""))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(apiPost("/authentication/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("idToken", "x".repeat(4097)))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("CP-WS-10: una cuenta social no entra por /sign-in con ninguna contrasena (401)")
    void cuentaSocialNoEntraPorContrasena() throws Exception {
        String email = uniqueEmail("social.only");
        google(googleToken(uniqueSub(), email, true, "Solo Social")).andExpect(status().isOk());

        for (String password : new String[]{"Sidru2026a", "Cualquier1234", "Admin1234"}) {
            mockMvc.perform(apiPost("/authentication/sign-in")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", email, "password", password))))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    @DisplayName("GET /authentication/providers lista Google y Apple con sus identificadores publicos")
    void providersListaLosHabilitados() throws Exception {
        mockMvc.perform(apiGet("/authentication/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password").value(true))
                .andExpect(jsonPath("$.google.clientId").value("test-google-client.apps.googleusercontent.com"))
                .andExpect(jsonPath("$.apple.serviceId").value("pe.sidru.web.test"))
                .andExpect(jsonPath("$.apple.redirectUri").value("https://sidru.example.test/auth/apple/callback"));
    }

    @Test
    @DisplayName("/sign-in sigue devolviendo newUser = false (campo aditivo)")
    void signInNoRompeElContrato() throws Exception {
        var user = newCitizen();
        mockMvc.perform(apiPost("/authentication/sign-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", user.email(), "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newUser").value(false));
    }
}

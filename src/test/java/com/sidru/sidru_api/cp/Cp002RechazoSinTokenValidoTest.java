package com.sidru.sidru_api.cp;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CP002 — Rechazo de acceso sin token valido.
 * HU: US-28 · Escenario 1 · Prioridad: Alta
 * Criterio de aceptacion: Rechazo de acceso sin token valido.
 *
 * Postcondicion: no se modifica ningun registro; el intento queda en el log de seguridad.
 */
@DisplayName("CP002 - Rechazo de acceso sin token valido")
class Cp002RechazoSinTokenValidoTest extends CpBaseTest {

    @Value("${authorization.jwt.secret}")
    private String jwtSecret;

    @Test
    @DisplayName("Paso 1-2: sin cabecera Authorization responde 401 con WWW-Authenticate: Bearer")
    void sinCabeceraDeAutorizacionDevuelve401() throws Exception {
        // Paso 1 — GET /api/v1/sessions/me sin la cabecera Authorization -> HTTP 401.
        // Paso 2 — La respuesta incluye la cabecera WWW-Authenticate: Bearer.
        mockMvc.perform(apiGet("/sessions/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"))
                .andExpect(header().string("WWW-Authenticate", Matchers.startsWith("Bearer")));
    }

    @Test
    @DisplayName("Paso 3: token manipulado y token expirado responden 401")
    void tokenManipuladoYTokenExpiradoDevuelven401() throws Exception {
        var user = newCitizen();

        // Token manipulado: se altera el primer caracter del payload, que siempre es 'e'
        // (base64url de '{'). Cambiarlo altera los bytes firmados con certeza, asi que la
        // firma deja de cuadrar. Alterar la firma en si no serviria: en base64url varios
        // caracteres finales decodifican a los mismos bytes y el token seguiria siendo valido.
        String[] parts = user.token().split("\\.");
        char[] payload = parts[1].toCharArray();
        payload[0] = payload[0] == 'e' ? 'f' : 'e';
        String tampered = parts[0] + "." + new String(payload) + "." + parts[2];

        mockMvc.perform(apiGet("/sessions/me").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());

        // Token expirado: firmado con la misma clave, pero con expiracion en el pasado.
        var key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        String expired = Jwts.builder()
                .subject(String.valueOf(user.id()))
                .issuedAt(new Date(System.currentTimeMillis() - 172_800_000L))   // hace 2 dias
                .expiration(new Date(System.currentTimeMillis() - 86_400_000L))  // expiro ayer
                .signWith(key)
                .compact();

        mockMvc.perform(apiGet("/sessions/me").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Paso 4: token con rol insuficiente sobre un endpoint de administracion responde 403")
    void tokenConRolInsuficienteDevuelve403() throws Exception {
        var citizen = newCitizen();

        // GET /users exige rol ADMIN: un ciudadano autenticado no pasa la autorizacion.
        mockMvc.perform(apiGet("/users").header("Authorization", citizen.bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Contraste: el mismo recurso con un token valido responde 200")
    void conTokenValidoElRecursoResponde200() throws Exception {
        var citizen = newCitizen();
        mockMvc.perform(apiGet("/sessions/me").header("Authorization", citizen.bearer()))
                .andExpect(status().isOk());
    }
}

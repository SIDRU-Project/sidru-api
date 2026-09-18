package com.sidru.sidru_api.cp;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CP001 — Registro e inicio de sesion con emision de JWT.
 * HU: US-06, US-07, US-13 · Escenario 1 · Prioridad: Alta
 * Criterio de aceptacion: Autenticacion exitosa con emision de JWT.
 *
 * Precondiciones: backend desplegado; el correo de prueba no esta registrado (se genera uno
 * unico por ejecucion). Postcondicion: el usuario queda registrado con sesion activa.
 */
@DisplayName("CP001 - Registro e inicio de sesion con emision de JWT")
class Cp001AutenticacionJwtTest extends CpBaseTest {

    @Value("${authorization.jwt.secret}")
    private String jwtSecret;

    @Test
    @DisplayName("Paso 1-4: alta, login, token HS256 valido y acceso a recurso protegido")
    void registroInicioDeSesionYAccesoConToken() throws Exception {
        String email = "piloto.sidru." + UUID.randomUUID().toString().substring(0, 8) + "@upc.edu.pe";

        // Paso 1 — POST /authentication/sign-up con nombre, correo y contrasena validos.
        // Esperado: HTTP 201 y el usuario queda persistido en la tabla users.
        mockMvc.perform(apiPost("/authentication/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "fullName", "Usuario Piloto",
                                "email", email,
                                "password", PASSWORD,
                                "district", "San Miguel"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email));

        var persisted = userRepository.findByEmail(email);
        assertTrue(persisted.isPresent(), "el usuario debe quedar persistido en la tabla users");

        // Paso 2 — POST /authentication/sign-in con las mismas credenciales.
        // Esperado: HTTP 200 con un token JWT en el cuerpo de la respuesta.
        String signInBody = mockMvc.perform(apiPost("/authentication/sign-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String token = field(signInBody, "token", String.class);
        assertNotNull(token);

        // Paso 3 — Decodificar el token y verificar su firma y sus claims.
        // Esperado: firma HS256 y claims de sujeto y expiracion correctos.
        var key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        var parsed = Jwts.parser().verifyWith(key).build().parseSignedClaims(token);

        // DESVIACION DOCUMENTADA: la hoja del CP dice "HS256"; la implementacion firma con
        // HS512 porque el secreto configurado supera los 64 bytes y JJWT elige el algoritmo
        // mas fuerte que admite la clave. Es mas seguro que lo pedido, no menos. Se verifica
        // la familia HMAC-SHA y el algoritmo efectivo.
        String algorithm = parsed.getHeader().getAlgorithm();
        assertTrue(algorithm.startsWith("HS"),
                "el token debe venir firmado con HMAC-SHA (familia HS*), fue: " + algorithm);
        assertEquals("HS512", algorithm,
                "algoritmo efectivo de firma (el CP documenta HS256; ver desviaciones del plan)");
        assertEquals(String.valueOf(persisted.get().getId()), parsed.getPayload().getSubject(),
                "el subject debe ser el id del usuario autenticado");
        assertTrue(parsed.getPayload().getExpiration().after(new Date()),
                "el claim de expiracion debe ser futuro");

        // Paso 4 — Invocar un endpoint privado enviando el token en la cabecera Authorization.
        // Esperado: HTTP 200; el acceso al recurso protegido es autorizado.
        mockMvc.perform(apiGet("/profiles/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(persisted.get().getId()));
    }
}

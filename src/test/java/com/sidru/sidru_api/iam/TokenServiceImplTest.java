package com.sidru.sidru_api.iam;

import com.sidru.sidru_api.iam.infrastructure.tokens.jwt.services.TokenServiceImpl;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Generación y validación de JWT (US-06/US-33). Cubre: emisión + validación + extracción
 * del userId, y los caminos de rechazo (malformado, firma ajena, expirado) y la extracción
 * del Bearer del header. Sin Spring ni red: se inyectan secret/expiración por reflexión.
 */
class TokenServiceImplTest {

    private static final String SECRET =
            "WG75r3R6Z2pYBnq8tNk4mLwVcUaQfPdHsJyXxKbEgF5T7C9D3uMoSeAhqHGNI4vYZx";
    private TokenServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TokenServiceImpl();
        ReflectionTestUtils.setField(service, "secret", SECRET);
        ReflectionTestUtils.setField(service, "expirationDays", 7);
    }

    @Test
    void generaYValidaUnTokenYExtraeElUserId() {
        String token = service.generateToken(42L);
        assertNotNull(token);
        assertTrue(service.validateToken(token));
        assertEquals(42L, service.getUserIdFromToken(token));
    }

    @Test
    void generaTokenDesdeAuthentication() {
        var auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("99");
        String token = service.generateToken(auth);
        assertEquals(99L, service.getUserIdFromToken(token));
    }

    @Test
    void rechazaUnTokenMalformado() {
        assertFalse(service.validateToken("no-es-un-jwt"));
    }

    @Test
    void rechazaUnTokenFirmadoConOtraClave() {
        var otherKey = Keys.hmacShaKeyFor(
                "OTRA-clave-distinta-de-32-bytes-o-mas-1234567890".getBytes(StandardCharsets.UTF_8));
        String foreign = Jwts.builder().subject("1").signWith(otherKey).compact();
        assertFalse(service.validateToken(foreign));
    }

    @Test
    void rechazaUnTokenExpirado() {
        ReflectionTestUtils.setField(service, "expirationDays", -1);  // expira ayer
        String expired = service.generateToken(7L);
        assertFalse(service.validateToken(expired));
    }

    @Test
    void extraeElBearerTokenDelHeader() {
        var req = mock(HttpServletRequest.class);
        when(req.getHeader("Authorization")).thenReturn("Bearer abc.def.ghi");
        assertEquals("abc.def.ghi", service.getBearerTokenFrom(req));
    }

    @Test
    void devuelveNullSinBearerValido() {
        var sinHeader = mock(HttpServletRequest.class);
        when(sinHeader.getHeader("Authorization")).thenReturn(null);
        assertNull(service.getBearerTokenFrom(sinHeader));

        var noBearer = mock(HttpServletRequest.class);
        when(noBearer.getHeader("Authorization")).thenReturn("Basic xyz");
        assertNull(service.getBearerTokenFrom(noBearer));
    }
}

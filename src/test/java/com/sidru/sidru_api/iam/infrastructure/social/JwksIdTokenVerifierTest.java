package com.sidru.sidru_api.iam.infrastructure.social;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sidru.sidru_api.iam.domain.model.exceptions.InvalidSocialTokenException;
import com.sidru.sidru_api.iam.domain.model.exceptions.SocialProviderUnavailableException;
import com.sidru.sidru_api.iam.domain.model.valueobjects.SocialProvider;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueba unitaria de {@link JwksIdTokenVerifier} con un JWKS local (claves RSA generadas aqui):
 * firma, iss, aud, exp y las variantes de email_verified. Sin red externa.
 */
@DisplayName("JwksIdTokenVerifier - ID tokens contra un JWKS local")
class JwksIdTokenVerifierTest {

    private static final String CLIENT_ID = "test-client.apps.googleusercontent.com";
    private static final String ISSUER = "https://accounts.google.com";

    private static RSAKey signingKey;
    private static RSAKey attackerKey;
    private static HttpServer jwksServer;
    private static JwksIdTokenVerifier verifier;

    @BeforeAll
    static void startJwks() throws Exception {
        signingKey = new RSAKeyGenerator(2048).keyID("k1").generate();
        attackerKey = new RSAKeyGenerator(2048).keyID("k1").generate(); // mismo kid, otra clave

        byte[] jwks = new JWKSet(signingKey.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
        jwksServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        jwksServer.createContext("/jwks", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, jwks.length);
            try (var out = exchange.getResponseBody()) {
                out.write(jwks);
            }
        });
        jwksServer.start();

        verifier = new JwksIdTokenVerifier(propertiesFor("http://127.0.0.1:" + jwksServer.getAddress().getPort() + "/jwks"));
    }

    @AfterAll
    static void stopJwks() {
        jwksServer.stop(0);
    }

    private static SocialAuthProperties propertiesFor(String jwksUri) {
        var properties = new SocialAuthProperties();
        properties.getGoogle().setClientIds(List.of(CLIENT_ID));
        properties.getGoogle().setJwksUri(jwksUri);
        return properties;
    }

    private static JWTClaimsSet.Builder validClaims() {
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(CLIENT_ID)
                .subject("1234567890")
                .claim("email", "ana.rios@gmail.com")
                .claim("email_verified", true)
                .claim("name", "Ana Ríos")
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(3600)));
    }

    private static String sign(JWTClaimsSet claims, RSAKey key) throws Exception {
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("k1").build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    @Test
    @DisplayName("token valido: devuelve sub, email, email_verified y nombre")
    void tokenValido() throws Exception {
        var identity = verifier.verify(SocialProvider.GOOGLE, sign(validClaims().build(), signingKey));
        assertEquals("1234567890", identity.subject());
        assertEquals("ana.rios@gmail.com", identity.email());
        assertTrue(identity.emailVerified());
        assertEquals("Ana Ríos", identity.name());
    }

    @Test
    @DisplayName("el claim nonce llega en VerifiedIdentity; sin el claim queda null")
    void nonceDelToken() throws Exception {
        var conNonce = validClaims().claim("nonce", "abc123").build();
        assertEquals("abc123", verifier.verify(SocialProvider.GOOGLE, sign(conNonce, signingKey)).nonce());
        assertNull(verifier.verify(SocialProvider.GOOGLE, sign(validClaims().build(), signingKey)).nonce());
    }

    @Test
    @DisplayName("issuer sin https:// (accounts.google.com) tambien es valido")
    void issuerAlternativo() throws Exception {
        var claims = validClaims().issuer("accounts.google.com").build();
        assertEquals("1234567890", verifier.verify(SocialProvider.GOOGLE, sign(claims, signingKey)).subject());
    }

    @Test
    @DisplayName("firma de otra clave (mismo kid) -> InvalidSocialTokenException")
    void firmaInvalida() throws Exception {
        assertThrows(InvalidSocialTokenException.class,
                () -> verifier.verify(SocialProvider.GOOGLE, sign(validClaims().build(), attackerKey)));
    }

    @Test
    @DisplayName("payload alterado despues de firmar -> InvalidSocialTokenException")
    void payloadAlterado() throws Exception {
        String token = sign(validClaims().build(), signingKey);
        String[] parts = token.split("\\.");
        String forged = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                validClaims().claim("email", "admin@sidru.pe").build().toString().getBytes(StandardCharsets.UTF_8));
        assertThrows(InvalidSocialTokenException.class,
                () -> verifier.verify(SocialProvider.GOOGLE, parts[0] + "." + forged + "." + parts[2]));
    }

    @Test
    @DisplayName("iss de otro emisor -> InvalidSocialTokenException")
    void emisorIncorrecto() throws Exception {
        var claims = validClaims().issuer("https://evil.example.com").build();
        assertThrows(InvalidSocialTokenException.class,
                () -> verifier.verify(SocialProvider.GOOGLE, sign(claims, signingKey)));
    }

    @Test
    @DisplayName("aud de otro client ID -> InvalidSocialTokenException")
    void audienciaIncorrecta() throws Exception {
        var claims = validClaims().audience("otro-client.apps.googleusercontent.com").build();
        assertThrows(InvalidSocialTokenException.class,
                () -> verifier.verify(SocialProvider.GOOGLE, sign(claims, signingKey)));
    }

    @Test
    @DisplayName("token vencido (mas alla de los 60 s de tolerancia) -> InvalidSocialTokenException")
    void tokenVencido() throws Exception {
        var claims = validClaims().issueTime(Date.from(Instant.now().minusSeconds(3600)))
                .expirationTime(Date.from(Instant.now().minusSeconds(300))).build();
        assertThrows(InvalidSocialTokenException.class,
                () -> verifier.verify(SocialProvider.GOOGLE, sign(claims, signingKey)));
    }

    @Test
    @DisplayName("vencido hace 30 s (dentro de la tolerancia de 60 s) -> aceptado")
    void toleranciaDeReloj() throws Exception {
        var claims = validClaims().issueTime(Date.from(Instant.now().minusSeconds(3600)))
                .expirationTime(Date.from(Instant.now().minusSeconds(30))).build();
        assertEquals("1234567890", verifier.verify(SocialProvider.GOOGLE, sign(claims, signingKey)).subject());
    }

    @Test
    @DisplayName("nbf en el futuro lejano -> InvalidSocialTokenException")
    void aunNoValido() throws Exception {
        var claims = validClaims().notBeforeTime(Date.from(Instant.now().plusSeconds(600))).build();
        assertThrows(InvalidSocialTokenException.class,
                () -> verifier.verify(SocialProvider.GOOGLE, sign(claims, signingKey)));
    }

    @Test
    @DisplayName("sin email o sin sub -> InvalidSocialTokenException")
    void sinEmailOSinSub() throws Exception {
        var sinEmail = new JWTClaimsSet.Builder().issuer(ISSUER).audience(CLIENT_ID).subject("1")
                .expirationTime(Date.from(Instant.now().plusSeconds(3600))).build();
        assertThrows(InvalidSocialTokenException.class,
                () -> verifier.verify(SocialProvider.GOOGLE, sign(sinEmail, signingKey)));

        var sinSub = new JWTClaimsSet.Builder().issuer(ISSUER).audience(CLIENT_ID).claim("email", "a@b.com")
                .expirationTime(Date.from(Instant.now().plusSeconds(3600))).build();
        assertThrows(InvalidSocialTokenException.class,
                () -> verifier.verify(SocialProvider.GOOGLE, sign(sinSub, signingKey)));
    }

    @Test
    @DisplayName("email_verified como cadena \"true\" (Apple) y como false; sin nombre queda null")
    void emailVerifiedVariantes() throws Exception {
        var comoCadena = validClaims().claim("email_verified", "true").build();
        assertTrue(verifier.verify(SocialProvider.GOOGLE, sign(comoCadena, signingKey)).emailVerified());

        var falso = validClaims().claim("email_verified", false).build();
        assertFalse(verifier.verify(SocialProvider.GOOGLE, sign(falso, signingKey)).emailVerified());

        var sinNombre = validClaims().claim("name", null).build();
        assertNull(verifier.verify(SocialProvider.GOOGLE, sign(sinNombre, signingKey)).name());
    }

    @Test
    @DisplayName("basura y token vacio -> InvalidSocialTokenException")
    void basura() {
        assertThrows(InvalidSocialTokenException.class, () -> verifier.verify(SocialProvider.GOOGLE, "no-es-jwt"));
        assertThrows(InvalidSocialTokenException.class, () -> verifier.verify(SocialProvider.GOOGLE, ""));
        assertThrows(InvalidSocialTokenException.class, () -> verifier.verify(SocialProvider.GOOGLE, null));
    }

    @Test
    @DisplayName("JWKS inaccesible -> SocialProviderUnavailableException (503), no un 401")
    void jwksCaido() throws Exception {
        var sinServidor = new JwksIdTokenVerifier(propertiesFor("http://127.0.0.1:1/jwks"));
        assertThrows(SocialProviderUnavailableException.class,
                () -> sinServidor.verify(SocialProvider.GOOGLE, sign(validClaims().build(), signingKey)));
    }
}

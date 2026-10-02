package com.sidru.sidru_api.iam.infrastructure.social;

import com.nimbusds.jose.RemoteKeySourceException;
import com.sidru.sidru_api.iam.application.internal.outboundservices.social.IdTokenVerifier;
import com.sidru.sidru_api.iam.application.internal.outboundservices.social.VerifiedIdentity;
import com.sidru.sidru_api.iam.domain.model.exceptions.InvalidSocialTokenException;
import com.sidru.sidru_api.iam.domain.model.exceptions.SocialProviderUnavailableException;
import com.sidru.sidru_api.iam.domain.model.valueobjects.SocialProvider;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Verifica ID tokens de Google y Apple con {@link NimbusJwtDecoder}: firma RS256 por JWKS,
 * {@code iss}, {@code aud} en los client IDs configurados y {@code exp}/{@code nbf} con 60 s de tolerancia.
 *
 * <p>Los decoders se crean una vez por proveedor (Nimbus cachea el JWKS y lo refresca si ve un
 * {@code kid} nuevo). Si el JWKS no responde se lanza {@link SocialProviderUnavailableException}
 * (503), no un 401: el token puede ser perfectamente válido.
 */
@Component
public class JwksIdTokenVerifier implements IdTokenVerifier {

    private static final Duration CLOCK_SKEW = Duration.ofSeconds(60);
    private static final int TIMEOUT_MILLIS = 5_000;

    private final SocialAuthProperties properties;
    private final Map<SocialProvider, NimbusJwtDecoder> decoders = new EnumMap<>(SocialProvider.class);

    public JwksIdTokenVerifier(SocialAuthProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean isEnabled(SocialProvider provider) {
        return properties.isEnabled(provider);
    }

    @Override
    public VerifiedIdentity verify(SocialProvider provider, String token) {
        if (token == null || token.isBlank()) throw new InvalidSocialTokenException();

        Jwt jwt;
        try {
            jwt = decoder(provider).decode(token);
        } catch (BadJwtException e) {
            throw new InvalidSocialTokenException("ID token rechazado: " + e.getMessage(), e);
        } catch (JwtException e) {
            if (causedByRemoteKeySource(e)) {
                throw new SocialProviderUnavailableException("JWKS no disponible: " + e.getMessage(), e);
            }
            throw new InvalidSocialTokenException("ID token rechazado: " + e.getMessage(), e);
        }

        String subject = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        if (subject == null || subject.isBlank() || email == null || email.isBlank()) {
            throw new InvalidSocialTokenException("El token no trae sub o email", null);
        }
        return new VerifiedIdentity(subject, email.trim(), isTrue(jwt.getClaim("email_verified")),
                jwt.getClaimAsString("name"));
    }

    /** Google envía un booleano; Apple a veces la cadena "true". */
    private static boolean isTrue(Object claim) {
        return claim instanceof Boolean b ? b : claim != null && "true".equalsIgnoreCase(claim.toString());
    }

    private static boolean causedByRemoteKeySource(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof RemoteKeySourceException) return true;
        }
        return false;
    }

    private synchronized NimbusJwtDecoder decoder(SocialProvider provider) {
        return decoders.computeIfAbsent(provider, this::buildDecoder);
    }

    private NimbusJwtDecoder buildDecoder(SocialProvider provider) {
        var config = properties.of(provider);

        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(TIMEOUT_MILLIS);
        requestFactory.setReadTimeout(TIMEOUT_MILLIS);

        var decoder = NimbusJwtDecoder.withJwkSetUri(config.getJwksUri())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .restOperations(new RestTemplate(requestFactory))
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(CLOCK_SKEW),
                claimIn("iss", config.getIssuers()),
                audienceIn(config.getClientIds())));
        return decoder;
    }

    private static OAuth2TokenValidator<Jwt> claimIn(String claim, List<String> allowed) {
        return jwt -> {
            Object value = jwt.getClaim(claim);
            return value != null && allowed.contains(value.toString())
                    ? OAuth2TokenValidatorResult.success()
                    : failure("invalid_" + claim);
        };
    }

    private static OAuth2TokenValidator<Jwt> audienceIn(List<String> clientIds) {
        return jwt -> {
            List<String> audience = jwt.getAudience();
            return audience != null && audience.stream().anyMatch(clientIds::contains)
                    ? OAuth2TokenValidatorResult.success()
                    : failure("invalid_audience");
        };
    }

    private static OAuth2TokenValidatorResult failure(String description) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", description, null));
    }
}

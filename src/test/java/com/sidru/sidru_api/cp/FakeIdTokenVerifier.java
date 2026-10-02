package com.sidru.sidru_api.cp;

import com.sidru.sidru_api.iam.application.internal.outboundservices.social.IdTokenVerifier;
import com.sidru.sidru_api.iam.application.internal.outboundservices.social.VerifiedIdentity;
import com.sidru.sidru_api.iam.domain.model.exceptions.InvalidSocialTokenException;
import com.sidru.sidru_api.iam.domain.model.valueobjects.SocialProvider;
import com.sidru.sidru_api.iam.infrastructure.social.SocialAuthProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Verificador falso del perfil {@code test}: los CP no salen a internet ni dependen de Google/Apple.
 * Token: {@code fake:<PROVIDER>:<sub>:<email>:<verified>:<name>[:<nonce>]}. Vive en src/test, así que nunca
 * llega al artefacto de producción.
 */
@Component
@Primary
@Profile("test")
class FakeIdTokenVerifier implements IdTokenVerifier {

    private final SocialAuthProperties properties;

    FakeIdTokenVerifier(SocialAuthProperties properties) {
        this.properties = properties;
    }

    static String token(SocialProvider provider, String sub, String email, boolean verified, String name) {
        return "fake:" + provider + ":" + sub + ":" + email + ":" + verified + ":" + (name == null ? "" : name);
    }

    static String token(SocialProvider provider, String sub, String email, boolean verified, String name,
                        String nonce) {
        return token(provider, sub, email, verified, name) + ":" + nonce;
    }

    @Override
    public boolean isEnabled(SocialProvider provider) {
        return properties.isEnabled(provider);
    }

    @Override
    public VerifiedIdentity verify(SocialProvider provider, String token) {
        String[] parts = token == null ? new String[0] : token.split(":", 7);
        if (parts.length < 6 || !"fake".equals(parts[0]) || !provider.name().equals(parts[1])
                || parts[2].isBlank() || parts[3].isBlank()) {
            throw new InvalidSocialTokenException();
        }
        return new VerifiedIdentity(parts[2], parts[3], Boolean.parseBoolean(parts[4]),
                parts[5].isBlank() ? null : parts[5], parts.length == 7 && !parts[6].isBlank() ? parts[6] : null);
    }
}

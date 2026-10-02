package com.sidru.sidru_api.iam.infrastructure.social;

import com.sidru.sidru_api.iam.domain.model.valueobjects.SocialProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuración {@code sidru.auth.*} del login social (spec sidru-web-social, D-WS-6).
 * Un proveedor sin {@code client-ids} queda deshabilitado.
 */
@Component
@ConfigurationProperties(prefix = "sidru.auth")
public class SocialAuthProperties {

    private final Google google = new Google();
    private final Apple apple = new Apple();

    public Google getGoogle() {
        return google;
    }

    public Apple getApple() {
        return apple;
    }

    public Provider of(SocialProvider provider) {
        return provider == SocialProvider.GOOGLE ? google : apple;
    }

    public boolean isEnabled(SocialProvider provider) {
        return !of(provider).getClientIds().isEmpty();
    }

    public static class Google extends Provider {
        public Google() {
            setJwksUri("https://www.googleapis.com/oauth2/v3/certs");
            setIssuers(List.of("accounts.google.com", "https://accounts.google.com"));
        }
    }

    public static class Apple extends Provider {
        private String redirectUri = "";

        public Apple() {
            setJwksUri("https://appleid.apple.com/auth/keys");
            setIssuers(List.of("https://appleid.apple.com"));
        }

        public String getRedirectUri() {
            return redirectUri;
        }

        public void setRedirectUri(String redirectUri) {
            this.redirectUri = redirectUri == null ? "" : redirectUri.trim();
        }
    }

    /** Datos comunes de un proveedor. JWKS e issuers son configurables para las pruebas. */
    public abstract static class Provider {
        private List<String> clientIds = new ArrayList<>();
        private String jwksUri;
        private List<String> issuers = new ArrayList<>();

        public List<String> getClientIds() {
            return clientIds;
        }

        public void setClientIds(List<String> clientIds) {
            this.clientIds = clientIds == null ? new ArrayList<>()
                    : clientIds.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
        }

        public String getJwksUri() {
            return jwksUri;
        }

        public void setJwksUri(String jwksUri) {
            this.jwksUri = jwksUri;
        }

        public List<String> getIssuers() {
            return issuers;
        }

        public void setIssuers(List<String> issuers) {
            this.issuers = issuers;
        }
    }
}

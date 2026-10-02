package com.sidru.sidru_api.iam.interfaces.rest.resources;

/**
 * Qué métodos de acceso ofrece el backend (D-WS-6). Un proveedor sin configurar se envía en null.
 * Los identificadores son públicos.
 */
public record AuthProvidersResource(boolean password, Google google, Apple apple) {

    public record Google(String clientId) {
    }

    public record Apple(String serviceId, String redirectUri) {
    }
}

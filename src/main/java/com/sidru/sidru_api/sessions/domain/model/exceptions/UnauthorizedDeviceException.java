package com.sidru.sidru_api.sessions.domain.model.exceptions;

/**
 * Un dispositivo presento una API key que no corresponde a ningun Smart Bin registrado
 * (CP008 / US-15). Lleva la credencial presentada para que la capa de interfaces pueda
 * asentar el intento en el log de auditoria; nunca se registra en claro (se enmascara
 * antes de persistir) ni se devuelve al cliente.
 */
public class UnauthorizedDeviceException extends RuntimeException {

    private final String presentedApiKey;

    public UnauthorizedDeviceException() {
        this(null);
    }

    public UnauthorizedDeviceException(String presentedApiKey) {
        this.presentedApiKey = presentedApiKey;
    }

    public String getPresentedApiKey() {
        return presentedApiKey;
    }
}

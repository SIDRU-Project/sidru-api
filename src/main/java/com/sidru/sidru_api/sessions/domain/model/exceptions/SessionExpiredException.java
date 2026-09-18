package com.sidru.sidru_api.sessions.domain.model.exceptions;

/**
 * El codigo QR de la sesion supero su vigencia (CP015 / US-23). Lleva el id de la sesion
 * para que la capa de interfaces pueda marcarla EXPIRED fuera de la transaccion de canje:
 * marcarla dentro seria inutil, porque lanzar esta excepcion revierte esa transaccion.
 */
public class SessionExpiredException extends RuntimeException {

    private final Long sessionId;

    public SessionExpiredException() {
        this(null);
    }

    public SessionExpiredException(Long sessionId) {
        this.sessionId = sessionId;
    }

    public Long getSessionId() {
        return sessionId;
    }
}

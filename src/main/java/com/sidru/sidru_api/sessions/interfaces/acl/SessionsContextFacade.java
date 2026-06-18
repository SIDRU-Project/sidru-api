package com.sidru.sidru_api.sessions.interfaces.acl;

/**
 * Puerto de solo lectura del contexto sessions hacia otros contextos (p.ej. metrics).
 * Expone agregados de negocio sin filtrar las entidades ni el repositorio (US-36).
 */
public interface SessionsContextFacade {
    long countAllSessions();
    long countConfirmedSessions();
    long sumConfirmedCaps();
    double sumConfirmedWeightGrams();
    long sumConfirmedPoints();
}

package com.sidru.sidru_api.audit.domain.model.valueobjects;

/**
 * Tipos de evento auditables. Se mantiene corto a proposito: el log de auditoria
 * registra intentos con relevancia de seguridad, no trazas de negocio.
 */
public enum AuditEventType {
    /** Un dispositivo intento abrir una sesion con una API key no registrada (CP008 / US-15). */
    DEVICE_AUTH_FAILURE
}

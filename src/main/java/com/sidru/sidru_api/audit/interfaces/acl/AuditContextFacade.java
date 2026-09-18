package com.sidru.sidru_api.audit.interfaces.acl;

/**
 * Puerto de escritura del contexto audit hacia los demas contextos. Se expone solo
 * la intencion ("registra este intento fallido"), nunca el agregado ni el repositorio.
 */
public interface AuditContextFacade {

    /**
     * Asienta un intento de apertura de sesion con una API key de dispositivo no registrada
     * (CP008 / US-15). La implementacion enmascara la credencial antes de persistirla.
     *
     * @param presentedApiKey API key presentada (se guarda solo su huella, nunca en claro)
     * @param origin          IP de origen de la peticion
     * @param detail          metodo + ruta del recurso solicitado
     */
    void recordDeviceAuthFailure(String presentedApiKey, String origin, String detail);
}

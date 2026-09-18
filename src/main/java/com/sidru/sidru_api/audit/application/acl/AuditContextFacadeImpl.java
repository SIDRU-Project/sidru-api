package com.sidru.sidru_api.audit.application.acl;

import com.sidru.sidru_api.audit.application.internal.commandservices.AuditLogService;
import com.sidru.sidru_api.audit.domain.model.valueobjects.AuditEventType;
import com.sidru.sidru_api.audit.interfaces.acl.AuditContextFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuditContextFacadeImpl implements AuditContextFacade {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditContextFacadeImpl.class);

    private final AuditLogService auditLogService;

    public AuditContextFacadeImpl(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Override
    public void recordDeviceAuthFailure(String presentedApiKey, String origin, String detail) {
        try {
            auditLogService.record(
                    AuditEventType.DEVICE_AUTH_FAILURE,
                    AuditLogService.fingerprint(presentedApiKey),
                    origin,
                    detail);
        } catch (Exception ex) {
            // Best-effort: si la auditoria falla, la peticion ya rechazada no debe cambiar de resultado.
            LOGGER.error("No se pudo asentar el intento de dispositivo no autorizado: {}", ex.getMessage());
        }
    }
}

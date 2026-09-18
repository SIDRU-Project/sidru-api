package com.sidru.sidru_api.sessions.interfaces.rest;

import com.sidru.sidru_api.audit.interfaces.acl.AuditContextFacade;
import com.sidru.sidru_api.sessions.application.internal.commandservices.SessionExpirationService;
import com.sidru.sidru_api.sessions.domain.model.exceptions.*;
import com.sidru.sidru_api.shared.interfaces.rest.resources.ErrorResource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.List;

import static com.sidru.sidru_api.sessions.infrastructure.utils.SessionsErrorCatalog.*;

@RestControllerAdvice(basePackages = "com.sidru.sidru_api.sessions")
public class SessionsControllerAdvice {

    /** Motivo legible por maquina del rechazo por vigencia (CP015). */
    public static final String REASON_QR_EXPIRED = "qr_expirado";

    private final AuditContextFacade auditContextFacade;
    private final SessionExpirationService sessionExpirationService;

    public SessionsControllerAdvice(AuditContextFacade auditContextFacade,
                                    SessionExpirationService sessionExpirationService) {
        this.auditContextFacade = auditContextFacade;
        this.sessionExpirationService = sessionExpirationService;
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(RecyclingSessionNotFoundException.class)
    public ErrorResource handleNotFound() {
        return build(SESSION_NOT_FOUND.getCode(), SESSION_NOT_FOUND.getMessage());
    }

    // 409: la sesión ya no está PENDING (CONFIRMED/EXPIRED/CANCELLED, o 2ª confirmación
    // concurrente). US-23.
    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(InvalidSessionStateException.class)
    public ErrorResource handleInvalidState() {
        return build(INVALID_SESSION_STATE.getCode(), INVALID_SESSION_STATE.getMessage());
    }

    /**
     * 410: el QR vencio. Ademas de responder, deja la sesion marcada como EXPIRED para que no
     * vuelva a intentarse. Se hace aqui, no en el servicio, porque la transaccion de canje ya
     * termino y libero el lock de la fila (CP015 / US-23).
     */
    @ResponseStatus(HttpStatus.GONE)
    @ExceptionHandler(SessionExpiredException.class)
    public ErrorResource handleExpired(SessionExpiredException exception) {
        sessionExpirationService.markExpired(exception.getSessionId());
        return build(SESSION_EXPIRED.getCode(), SESSION_EXPIRED.getMessage(),
                List.of("reason: " + REASON_QR_EXPIRED));
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(InvalidCapCountException.class)
    public ErrorResource handleInvalidCaps() {
        return build(INVALID_CAP_COUNT.getCode(), INVALID_CAP_COUNT.getMessage(),
                List.of("capCount: " + INVALID_CAP_COUNT.getMessage()));
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(InvalidSessionWeightException.class)
    public ErrorResource handleInvalidWeight() {
        return build(INVALID_SESSION_WEIGHT.getCode(), INVALID_SESSION_WEIGHT.getMessage(),
                List.of("weightGrams: " + INVALID_SESSION_WEIGHT.getMessage()));
    }

    /**
     * Payload que no pasa las validaciones de bean (p. ej. falta capCount). Devuelve 400 con
     * el mismo formato que el resto de rechazos: cada detalle es "campo: motivo" (CP007).
     * Sin este handler, el contexto sessions caia en el /error generico, que no dice que campo
     * se rechazo.
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ErrorResource handleInvalidPayload(MethodArgumentNotValidException exception) {
        BindingResult result = exception.getBindingResult();
        return build(INVALID_PAYLOAD.getCode(), INVALID_PAYLOAD.getMessage(),
                result.getFieldErrors().stream()
                        .map(error -> error.getField() + ": " + error.getDefaultMessage())
                        .sorted()
                        .toList());
    }

    /** Falta la cabecera X-Device-Api-Key: es parte del contrato de apertura de sesion. */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ErrorResource handleMissingHeader(MissingRequestHeaderException exception) {
        return build(INVALID_PAYLOAD.getCode(), INVALID_PAYLOAD.getMessage(),
                List.of(exception.getHeaderName() + ": cabecera obligatoria ausente"));
    }

    /**
     * 403 (no 401): la peticion trae credencial, solo que no corresponde a ningun Smart Bin
     * registrado. El intento queda asentado en el log de auditoria con fecha, huella de la
     * credencial y origen (CP008 / US-15).
     */
    @ResponseStatus(HttpStatus.FORBIDDEN)
    @ExceptionHandler(UnauthorizedDeviceException.class)
    public ErrorResource handleUnauthorizedDevice(UnauthorizedDeviceException exception,
                                                  HttpServletRequest request) {
        auditContextFacade.recordDeviceAuthFailure(
                exception.getPresentedApiKey(),
                request.getRemoteAddr(),
                request.getMethod() + " " + request.getRequestURI());
        return build(UNAUTHORIZED_DEVICE.getCode(), UNAUTHORIZED_DEVICE.getMessage());
    }

    private ErrorResource build(String code, String message) {
        return build(code, message, null);
    }

    private ErrorResource build(String code, String message, List<String> details) {
        var r = new ErrorResource();
        r.setCode(code);
        r.setMessage(message);
        r.setDetails(details);
        r.setTimeStamp(LocalDateTime.now());
        return r;
    }
}

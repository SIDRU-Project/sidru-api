package com.sidru.sidru_api.shared.interfaces.rest;

import com.sidru.sidru_api.shared.interfaces.rest.resources.ErrorResource;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

/**
 * Traduce los fallos de autorizacion a su codigo HTTP correcto, en todos los contextos.
 *
 * <p>Sin este advice, el {@code AccessDeniedException} que lanza {@code @PreAuthorize} se
 * resolvia en el {@code @ExceptionHandler(Exception.class)} generico del contexto y salia
 * como <b>500</b>, no como 403: un usuario con rol insuficiente recibia "error interno"
 * en vez de "prohibido" (CP002, escenario 1, paso 4).</p>
 *
 * <p>{@code HIGHEST_PRECEDENCE}: debe consultarse antes que cualquier advice de contexto
 * con un handler atrapa-todo.</p>
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class SecurityControllerAdvice {

    /** Autenticado, pero sin el rol o permiso exigido por el recurso. */
    @ResponseStatus(HttpStatus.FORBIDDEN)
    @ExceptionHandler(AccessDeniedException.class)
    public ErrorResource handleAccessDenied() {
        return build("ERR_SEC_403", "No cuentas con permisos para acceder a este recurso");
    }

    /** Credencial ausente o invalida al resolver la autenticacion dentro del controller. */
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    @ExceptionHandler(AuthenticationException.class)
    public ErrorResource handleAuthentication() {
        return build("ERR_SEC_401", "Autenticacion requerida o credencial invalida");
    }

    private ErrorResource build(String code, String message) {
        var resource = new ErrorResource();
        resource.setCode(code);
        resource.setMessage(message);
        resource.setTimeStamp(LocalDateTime.now());
        return resource;
    }
}

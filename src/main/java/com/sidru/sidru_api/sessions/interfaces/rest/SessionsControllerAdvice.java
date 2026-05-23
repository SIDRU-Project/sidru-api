package com.sidru.sidru_api.sessions.interfaces.rest;

import com.sidru.sidru_api.sessions.domain.model.exceptions.*;
import com.sidru.sidru_api.shared.interfaces.rest.resources.ErrorResource;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

import static com.sidru.sidru_api.sessions.infrastructure.utils.SessionsErrorCatalog.*;

@RestControllerAdvice(basePackages = "com.sidru.sidru_api.sessions")
public class SessionsControllerAdvice {

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(RecyclingSessionNotFoundException.class)
    public ErrorResource handleNotFound() {
        return build(SESSION_NOT_FOUND.getCode(), SESSION_NOT_FOUND.getMessage());
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(InvalidSessionStateException.class)
    public ErrorResource handleInvalidState() {
        return build(INVALID_SESSION_STATE.getCode(), INVALID_SESSION_STATE.getMessage());
    }

    @ResponseStatus(HttpStatus.GONE)
    @ExceptionHandler(SessionExpiredException.class)
    public ErrorResource handleExpired() {
        return build(SESSION_EXPIRED.getCode(), SESSION_EXPIRED.getMessage());
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(InvalidCapCountException.class)
    public ErrorResource handleInvalidCaps() {
        return build(INVALID_CAP_COUNT.getCode(), INVALID_CAP_COUNT.getMessage());
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(InvalidSessionWeightException.class)
    public ErrorResource handleInvalidWeight() {
        return build(INVALID_SESSION_WEIGHT.getCode(), INVALID_SESSION_WEIGHT.getMessage());
    }

    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    @ExceptionHandler(UnauthorizedDeviceException.class)
    public ErrorResource handleUnauthorizedDevice() {
        return build(UNAUTHORIZED_DEVICE.getCode(), UNAUTHORIZED_DEVICE.getMessage());
    }

    private ErrorResource build(String code, String message) {
        var r = new ErrorResource();
        r.setCode(code);
        r.setMessage(message);
        r.setTimeStamp(LocalDateTime.now());
        return r;
    }
}

package com.sidru.sidru_api.devices.interfaces.rest;

import com.sidru.sidru_api.devices.domain.model.exceptions.DeviceCodeAlreadyExistsException;
import com.sidru.sidru_api.devices.domain.model.exceptions.SmartBinNotFoundException;
import com.sidru.sidru_api.shared.interfaces.rest.resources.ErrorResource;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

import static com.sidru.sidru_api.devices.infrastructure.utils.DevicesErrorCatalog.*;

@RestControllerAdvice(basePackages = "com.sidru.sidru_api.devices")
public class DevicesControllerAdvice {

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(SmartBinNotFoundException.class)
    public ErrorResource handleSmartBinNotFound() {
        var r = new ErrorResource();
        r.setCode(SMART_BIN_NOT_FOUND.getCode());
        r.setMessage(SMART_BIN_NOT_FOUND.getMessage());
        r.setTimeStamp(LocalDateTime.now());
        return r;
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(DeviceCodeAlreadyExistsException.class)
    public ErrorResource handleDeviceCodeExists() {
        var r = new ErrorResource();
        r.setCode(DEVICE_CODE_ALREADY_EXISTS.getCode());
        r.setMessage(DEVICE_CODE_ALREADY_EXISTS.getMessage());
        r.setTimeStamp(LocalDateTime.now());
        return r;
    }
}

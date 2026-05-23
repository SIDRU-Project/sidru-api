package com.sidru.sidru_api.users.interfaces.rest;

import com.sidru.sidru_api.shared.interfaces.rest.resources.ErrorResource;
import com.sidru.sidru_api.users.domain.model.exceptions.InsufficientPointsException;
import com.sidru.sidru_api.users.domain.model.exceptions.UserProfileNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

import static com.sidru.sidru_api.users.infrastructure.utils.UsersErrorCatalog.*;

@RestControllerAdvice(basePackages = "com.sidru.sidru_api.users")
public class UsersControllerAdvice {

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(UserProfileNotFoundException.class)
    public ErrorResource handleUserProfileNotFound() {
        var response = new ErrorResource();
        response.setCode(USER_PROFILE_NOT_FOUND.getCode());
        response.setMessage(USER_PROFILE_NOT_FOUND.getMessage());
        response.setTimeStamp(LocalDateTime.now());
        return response;
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(InsufficientPointsException.class)
    public ErrorResource handleInsufficientPoints() {
        var response = new ErrorResource();
        response.setCode(INSUFFICIENT_POINTS.getCode());
        response.setMessage(INSUFFICIENT_POINTS.getMessage());
        response.setTimeStamp(LocalDateTime.now());
        return response;
    }
}

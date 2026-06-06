package com.sidru.sidru_api.rewards.interfaces.rest;

import com.sidru.sidru_api.rewards.domain.model.exceptions.InsufficientPointsToRedeemException;
import com.sidru.sidru_api.rewards.domain.model.exceptions.RewardNotFoundException;
import com.sidru.sidru_api.rewards.domain.model.exceptions.RewardOutOfStockException;
import com.sidru.sidru_api.shared.interfaces.rest.resources.ErrorResource;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

import static com.sidru.sidru_api.rewards.infrastructure.utils.RewardsErrorCatalog.*;

@RestControllerAdvice(basePackages = "com.sidru.sidru_api.rewards")
public class RewardsControllerAdvice {

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(RewardNotFoundException.class)
    public ErrorResource handleNotFound() {
        return build(REWARD_NOT_FOUND.getCode(), REWARD_NOT_FOUND.getMessage());
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(RewardOutOfStockException.class)
    public ErrorResource handleOutOfStock() {
        return build(REWARD_OUT_OF_STOCK.getCode(), REWARD_OUT_OF_STOCK.getMessage());
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(InsufficientPointsToRedeemException.class)
    public ErrorResource handleInsufficientPoints() {
        return build(INSUFFICIENT_POINTS.getCode(), INSUFFICIENT_POINTS.getMessage());
    }

    private ErrorResource build(String code, String message) {
        var r = new ErrorResource();
        r.setCode(code);
        r.setMessage(message);
        r.setTimeStamp(LocalDateTime.now());
        return r;
    }
}

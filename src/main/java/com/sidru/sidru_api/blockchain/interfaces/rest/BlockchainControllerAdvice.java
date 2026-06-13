package com.sidru.sidru_api.blockchain.interfaces.rest;

import com.sidru.sidru_api.blockchain.domain.model.exceptions.InvalidWithdrawalAddressException;
import com.sidru.sidru_api.blockchain.domain.model.exceptions.NoBalanceToWithdrawException;
import com.sidru.sidru_api.blockchain.domain.model.exceptions.WithdrawalInProgressException;
import com.sidru.sidru_api.shared.interfaces.rest.resources.ErrorResource;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

import static com.sidru.sidru_api.blockchain.infrastructure.utils.BlockchainErrorCatalog.*;

@RestControllerAdvice(basePackages = "com.sidru.sidru_api.blockchain")
public class BlockchainControllerAdvice {

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(InvalidWithdrawalAddressException.class)
    public ErrorResource handleInvalidAddress() {
        return build(INVALID_ADDRESS.getCode(), INVALID_ADDRESS.getMessage());
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(WithdrawalInProgressException.class)
    public ErrorResource handleInProgress() {
        return build(WITHDRAWAL_IN_PROGRESS.getCode(), WITHDRAWAL_IN_PROGRESS.getMessage());
    }

    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    @ExceptionHandler(NoBalanceToWithdrawException.class)
    public ErrorResource handleNoBalance() {
        return build(NO_BALANCE.getCode(), NO_BALANCE.getMessage());
    }

    private ErrorResource build(String code, String message) {
        var r = new ErrorResource();
        r.setCode(code);
        r.setMessage(message);
        r.setTimeStamp(LocalDateTime.now());
        return r;
    }
}

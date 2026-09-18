package com.sidru.sidru_api.blockchain.interfaces.rest;

import com.sidru.sidru_api.blockchain.domain.model.exceptions.BelowMinimumWithdrawalException;
import com.sidru.sidru_api.blockchain.domain.model.exceptions.InvalidWithdrawalAddressException;
import com.sidru.sidru_api.blockchain.domain.model.exceptions.NoBalanceToWithdrawException;
import com.sidru.sidru_api.blockchain.domain.model.exceptions.WithdrawalInProgressException;
import com.sidru.sidru_api.blockchain.domain.model.exceptions.WithdrawalRequestNotFoundException;
import com.sidru.sidru_api.blockchain.domain.model.exceptions.WithdrawalsDisabledException;
import com.sidru.sidru_api.blockchain.infrastructure.web3j.config.WithdrawalProperties;
import com.sidru.sidru_api.shared.interfaces.rest.resources.ErrorResource;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.List;

import static com.sidru.sidru_api.blockchain.infrastructure.utils.BlockchainErrorCatalog.*;

@RestControllerAdvice(basePackages = "com.sidru.sidru_api.blockchain")
public class BlockchainControllerAdvice {

    private final WithdrawalProperties withdrawalProperties;

    public BlockchainControllerAdvice(WithdrawalProperties withdrawalProperties) {
        this.withdrawalProperties = withdrawalProperties;
    }

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

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(BelowMinimumWithdrawalException.class)
    public ErrorResource handleBelowMinimum() {
        int min = withdrawalProperties.getMinPoints();
        return build(MIN_WITHDRAWAL.getCode(), String.format(MIN_WITHDRAWAL.getMessage(), min),
                List.of("points: mínimo " + min));
    }

    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    @ExceptionHandler(WithdrawalsDisabledException.class)
    public ErrorResource handleWithdrawalsDisabled() {
        return build(WITHDRAWALS_DISABLED.getCode(), WITHDRAWALS_DISABLED.getMessage());
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(WithdrawalRequestNotFoundException.class)
    public ErrorResource handleWithdrawalNotFound() {
        return build(WITHDRAWAL_NOT_FOUND.getCode(), WITHDRAWAL_NOT_FOUND.getMessage());
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

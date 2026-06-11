package com.sidru.sidru_api.blockchain.interfaces.rest.transform;

import com.sidru.sidru_api.blockchain.domain.model.aggregates.WithdrawalRequest;
import com.sidru.sidru_api.blockchain.interfaces.rest.resources.WithdrawalStatusResource;

public class WithdrawalStatusResourceFromEntityAssembler {

    private static final String EXPLORER_TX = "https://amoy.polygonscan.com/tx/";

    private WithdrawalStatusResourceFromEntityAssembler() {}

    public static WithdrawalStatusResource toResourceFromEntity(WithdrawalRequest entity) {
        String explorerUrl = entity.getTxHash() != null ? EXPLORER_TX + entity.getTxHash() : null;
        return new WithdrawalStatusResource(
                entity.getId(),
                entity.getToAddress(),
                entity.getAmountWei(),
                entity.getStatus().name(),
                entity.getTxHash(),
                explorerUrl);
    }
}

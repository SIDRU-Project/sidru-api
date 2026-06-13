package com.sidru.sidru_api.rewards.interfaces.rest.transform;

import com.sidru.sidru_api.rewards.domain.model.aggregates.PointTransaction;
import com.sidru.sidru_api.rewards.interfaces.rest.resources.PointTransactionResource;

public class PointTransactionResourceFromEntityAssembler {

    public static PointTransactionResource toResourceFromEntity(PointTransaction t) {
        return new PointTransactionResource(
                t.getId(),
                t.getUserId(),
                t.getType(),
                t.getPoints(),
                t.getDescription(),
                t.getBlockchainTxHash(),
                t.getCreatedAt()
        );
    }
}

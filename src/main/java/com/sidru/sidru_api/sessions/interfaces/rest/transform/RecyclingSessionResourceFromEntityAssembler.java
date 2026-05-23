package com.sidru.sidru_api.sessions.interfaces.rest.transform;

import com.sidru.sidru_api.sessions.domain.model.aggregates.RecyclingSession;
import com.sidru.sidru_api.sessions.interfaces.rest.resources.RecyclingSessionResource;

public class RecyclingSessionResourceFromEntityAssembler {

    public static RecyclingSessionResource toResourceFromEntity(RecyclingSession s) {
        return new RecyclingSessionResource(
                s.getId(),
                s.getSmartBinId(),
                s.getUserId(),
                s.getCapCount(),
                s.getWeightGrams(),
                s.getPointsEarned(),
                s.getQrToken(),
                s.getStatus(),
                s.getExpiresAt(),
                s.getConfirmedAt(),
                s.getBlockchainTxHash()
        );
    }
}

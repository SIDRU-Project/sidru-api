package com.sidru.sidru_api.rewards.interfaces.rest.transform;

import com.sidru.sidru_api.rewards.domain.model.aggregates.Reward;
import com.sidru.sidru_api.rewards.interfaces.rest.resources.RewardResource;

public class RewardResourceFromEntityAssembler {

    public static RewardResource toResourceFromEntity(Reward r) {
        return new RewardResource(r.getId(), r.getName(), r.getDescription(),
                r.getPointsCost(), r.getStock(), r.isActive(), r.getImageUrl());
    }
}

package com.sidru.sidru_api.rewards.interfaces.rest.transform;

import com.sidru.sidru_api.rewards.domain.model.commands.CreateRewardCommand;
import com.sidru.sidru_api.rewards.interfaces.rest.resources.CreateRewardResource;

public class CreateRewardCommandFromResourceAssembler {

    public static CreateRewardCommand toCommandFromResource(CreateRewardResource resource) {
        return new CreateRewardCommand(resource.name(), resource.description(),
                resource.pointsCost(), resource.stock(), resource.imageUrl());
    }
}

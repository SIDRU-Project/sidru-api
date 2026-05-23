package com.sidru.sidru_api.sessions.interfaces.rest.transform;

import com.sidru.sidru_api.sessions.domain.model.commands.CreateRecyclingSessionCommand;
import com.sidru.sidru_api.sessions.interfaces.rest.resources.CreateRecyclingSessionResource;

public class CreateRecyclingSessionCommandFromResourceAssembler {

    public static CreateRecyclingSessionCommand toCommandFromResource(String deviceApiKey,
                                                                      CreateRecyclingSessionResource resource) {
        return new CreateRecyclingSessionCommand(deviceApiKey, resource.capCount(), resource.weightGrams());
    }
}

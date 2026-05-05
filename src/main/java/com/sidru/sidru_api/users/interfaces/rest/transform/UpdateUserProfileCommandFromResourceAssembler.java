package com.sidru.sidru_api.users.interfaces.rest.transform;

import com.sidru.sidru_api.users.domain.model.commands.UpdateUserProfileCommand;
import com.sidru.sidru_api.users.interfaces.rest.resources.UpdateUserProfileResource;

public class UpdateUserProfileCommandFromResourceAssembler {

    public static UpdateUserProfileCommand toCommandFromResource(Long userId,
                                                                 UpdateUserProfileResource resource) {
        return new UpdateUserProfileCommand(userId, resource.fullName(),
                resource.phone(), resource.district());
    }
}

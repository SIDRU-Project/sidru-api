package com.sidru.sidru_api.iam.interfaces.rest.transform;

import com.sidru.sidru_api.iam.domain.model.aggregates.User;
import com.sidru.sidru_api.iam.interfaces.rest.resources.AuthenticatedUserResource;

public class AuthenticatedUserResourceFromEntityAssembler {

    public static AuthenticatedUserResource toResourceFromEntity(User user, String token) {
        return new AuthenticatedUserResource(user.getId(), user.getEmail(), token);
    }
}

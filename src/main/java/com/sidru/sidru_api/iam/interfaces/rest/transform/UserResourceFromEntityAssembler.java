package com.sidru.sidru_api.iam.interfaces.rest.transform;

import com.sidru.sidru_api.iam.domain.model.aggregates.User;
import com.sidru.sidru_api.iam.domain.model.entities.Role;
import com.sidru.sidru_api.iam.interfaces.rest.resources.UserResource;

public class UserResourceFromEntityAssembler {

    public static UserResource toResourceFromEntity(User user) {
        var roles = user.getRoles().stream()
                .map(Role::getStringName)
                .toList();
        return new UserResource(user.getId(), user.getEmail(), roles);
    }
}

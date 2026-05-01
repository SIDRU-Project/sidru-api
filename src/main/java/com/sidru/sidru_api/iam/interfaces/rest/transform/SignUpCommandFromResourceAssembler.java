package com.sidru.sidru_api.iam.interfaces.rest.transform;

import com.sidru.sidru_api.iam.domain.model.commands.SignUpCommand;
import com.sidru.sidru_api.iam.domain.model.entities.Role;
import com.sidru.sidru_api.iam.interfaces.rest.resources.SignUpResource;

import java.util.ArrayList;

public class SignUpCommandFromResourceAssembler {

    public static SignUpCommand toCommandFromResource(SignUpResource resource) {
        var roles = resource.roles() != null
                ? resource.roles().stream().map(Role::toRoleFromName).toList()
                : new ArrayList<Role>();
        return new SignUpCommand(
                resource.fullName(),
                resource.email(),
                resource.password(),
                resource.phone(),
                resource.district(),
                roles
        );
    }
}

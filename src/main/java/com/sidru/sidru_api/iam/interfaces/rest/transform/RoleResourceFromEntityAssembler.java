package com.sidru.sidru_api.iam.interfaces.rest.transform;

import com.sidru.sidru_api.iam.domain.model.entities.Role;
import com.sidru.sidru_api.iam.interfaces.rest.resources.RoleResource;

public class RoleResourceFromEntityAssembler {

    public static RoleResource toResourceFromEntity(Role role) {
        return new RoleResource(role.getId(), role.getStringName());
    }
}

package com.sidru.sidru_api.iam.domain.services;

import com.sidru.sidru_api.iam.domain.model.entities.Role;
import com.sidru.sidru_api.iam.domain.model.queries.GetAllRolesQuery;
import com.sidru.sidru_api.iam.domain.model.queries.GetRoleByNameQuery;

import java.util.List;
import java.util.Optional;

public interface RoleQueryService {
    List<Role> handle(GetAllRolesQuery query);
    Optional<Role> handle(GetRoleByNameQuery query);
}

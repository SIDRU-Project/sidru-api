package com.sidru.sidru_api.iam.domain.model.queries;

import com.sidru.sidru_api.iam.domain.model.valueobjects.Roles;

public record GetRoleByNameQuery(Roles name) {
}

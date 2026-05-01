package com.sidru.sidru_api.iam.domain.services;

import com.sidru.sidru_api.iam.domain.model.commands.SeedRolesCommand;

public interface RoleCommandService {
    void handle(SeedRolesCommand command);
}

package com.sidru.sidru_api.iam.domain.model.commands;

import com.sidru.sidru_api.iam.domain.model.entities.Role;

import java.util.List;

public record SignUpCommand(
        String fullName,
        String email,
        String password,
        String phone,
        String district,
        List<Role> roles) {
}

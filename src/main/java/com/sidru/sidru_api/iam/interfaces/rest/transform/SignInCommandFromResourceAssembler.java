package com.sidru.sidru_api.iam.interfaces.rest.transform;

import com.sidru.sidru_api.iam.domain.model.commands.SignInCommand;
import com.sidru.sidru_api.iam.interfaces.rest.resources.SignInResource;

public class SignInCommandFromResourceAssembler {

    public static SignInCommand toCommandFromResource(SignInResource signInResource) {
        return new SignInCommand(signInResource.email(), signInResource.password());
    }
}

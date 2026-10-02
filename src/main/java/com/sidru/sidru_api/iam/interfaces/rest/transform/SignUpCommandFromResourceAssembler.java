package com.sidru.sidru_api.iam.interfaces.rest.transform;

import com.sidru.sidru_api.iam.domain.model.commands.SignUpCommand;
import com.sidru.sidru_api.iam.interfaces.rest.resources.SignUpResource;

import java.util.List;

public class SignUpCommandFromResourceAssembler {

    /**
     * El registro público siempre crea ciudadanos: los {@code roles} que envíe el cliente se
     * ignoran (con la lista vacía, {@code UserCommandServiceImpl} asigna ROLE_CITIZEN). El
     * campo se conserva en {@link SignUpResource} porque la app lo envía con ROLE_CITIZEN.
     */
    public static SignUpCommand toCommandFromResource(SignUpResource resource) {
        return new SignUpCommand(
                resource.fullName(),
                resource.email(),
                resource.password(),
                resource.phone(),
                resource.district(),
                List.of()
        );
    }
}

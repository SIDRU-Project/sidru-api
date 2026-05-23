package com.sidru.sidru_api.users.interfaces.rest.transform;

import com.sidru.sidru_api.users.domain.model.aggregates.UserProfile;
import com.sidru.sidru_api.users.interfaces.rest.resources.UserProfileResource;

public class UserProfileResourceFromEntityAssembler {

    public static UserProfileResource toResourceFromEntity(UserProfile profile) {
        return new UserProfileResource(
                profile.getId(),
                profile.getUserId(),
                profile.getFullName(),
                profile.getPhone(),
                profile.getDistrict(),
                profile.getTotalPoints(),
                profile.getTotalCaps(),
                profile.getTotalSessions()
        );
    }
}

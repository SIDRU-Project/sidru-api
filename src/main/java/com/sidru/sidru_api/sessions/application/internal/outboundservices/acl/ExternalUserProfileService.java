package com.sidru.sidru_api.sessions.application.internal.outboundservices.acl;

import com.sidru.sidru_api.users.interfaces.acl.UserProfileContextFacade;
import org.springframework.stereotype.Service;

@Service("sessionsExternalUserProfileService")
public class ExternalUserProfileService {

    private final UserProfileContextFacade userProfileContextFacade;

    public ExternalUserProfileService(UserProfileContextFacade userProfileContextFacade) {
        this.userProfileContextFacade = userProfileContextFacade;
    }

    public Long addPointsAndCaps(Long userId, int points, int caps) {
        return userProfileContextFacade.addPointsAndCaps(userId, points, caps);
    }
}

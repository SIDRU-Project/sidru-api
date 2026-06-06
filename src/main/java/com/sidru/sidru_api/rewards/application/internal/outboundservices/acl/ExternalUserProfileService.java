package com.sidru.sidru_api.rewards.application.internal.outboundservices.acl;

import com.sidru.sidru_api.users.interfaces.acl.UserProfileContextFacade;
import org.springframework.stereotype.Service;

@Service("rewardsExternalUserProfileService")
public class ExternalUserProfileService {

    private final UserProfileContextFacade userProfileContextFacade;

    public ExternalUserProfileService(UserProfileContextFacade userProfileContextFacade) {
        this.userProfileContextFacade = userProfileContextFacade;
    }

    public Integer fetchTotalPoints(Long userId) {
        return userProfileContextFacade.fetchTotalPointsByUserId(userId);
    }

    public Long subtractPoints(Long userId, int points) {
        return userProfileContextFacade.subtractPoints(userId, points);
    }
}

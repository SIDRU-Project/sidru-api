package com.sidru.sidru_api.iam.application.internal.outboundservices.acl;

import com.sidru.sidru_api.users.interfaces.acl.UserProfileContextFacade;
import org.springframework.stereotype.Service;

@Service("iamExternalProfileService")
public class ExternalProfileService {

    private final UserProfileContextFacade profileContextFacade;

    public ExternalProfileService(UserProfileContextFacade profileContextFacade) {
        this.profileContextFacade = profileContextFacade;
    }

    public Long createProfile(Long userId, String fullName, String phone, String district) {
        return profileContextFacade.createProfile(userId, fullName, phone, district);
    }
}

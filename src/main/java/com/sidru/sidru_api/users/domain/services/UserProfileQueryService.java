package com.sidru.sidru_api.users.domain.services;

import com.sidru.sidru_api.users.domain.model.aggregates.UserProfile;
import com.sidru.sidru_api.users.domain.model.queries.GetAllUserProfilesQuery;
import com.sidru.sidru_api.users.domain.model.queries.GetUserProfileByUserIdQuery;

import java.util.List;
import java.util.Optional;

public interface UserProfileQueryService {
    Optional<UserProfile> handle(GetUserProfileByUserIdQuery query);
    List<UserProfile> handle(GetAllUserProfilesQuery query);
}

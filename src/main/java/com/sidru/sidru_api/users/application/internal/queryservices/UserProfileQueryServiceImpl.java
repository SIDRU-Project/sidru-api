package com.sidru.sidru_api.users.application.internal.queryservices;

import com.sidru.sidru_api.users.domain.model.aggregates.UserProfile;
import com.sidru.sidru_api.users.domain.model.queries.GetAllUserProfilesQuery;
import com.sidru.sidru_api.users.domain.model.queries.GetUserProfileByUserIdQuery;
import com.sidru.sidru_api.users.domain.services.UserProfileQueryService;
import com.sidru.sidru_api.users.infrastructure.persistence.jpa.repositories.UserProfileRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserProfileQueryServiceImpl implements UserProfileQueryService {

    private final UserProfileRepository userProfileRepository;

    public UserProfileQueryServiceImpl(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
    }

    @Override
    public Optional<UserProfile> handle(GetUserProfileByUserIdQuery query) {
        return userProfileRepository.findByUserId(query.userId());
    }

    @Override
    public List<UserProfile> handle(GetAllUserProfilesQuery query) {
        return userProfileRepository.findAll();
    }
}

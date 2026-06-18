package com.sidru.sidru_api.users.application.acl;

import com.sidru.sidru_api.users.domain.model.commands.AddPointsCommand;
import com.sidru.sidru_api.users.domain.model.commands.CreateUserProfileCommand;
import com.sidru.sidru_api.users.domain.model.commands.SubtractPointsCommand;
import com.sidru.sidru_api.users.domain.model.queries.GetUserProfileByUserIdQuery;
import com.sidru.sidru_api.users.domain.services.UserProfileCommandService;
import com.sidru.sidru_api.users.domain.services.UserProfileQueryService;
import com.sidru.sidru_api.users.infrastructure.persistence.jpa.repositories.UserProfileRepository;
import com.sidru.sidru_api.users.interfaces.acl.UserProfileContextFacade;
import org.springframework.stereotype.Service;

@Service
public class UserProfileContextFacadeImpl implements UserProfileContextFacade {

    private final UserProfileCommandService userProfileCommandService;
    private final UserProfileQueryService userProfileQueryService;
    private final UserProfileRepository userProfileRepository;

    public UserProfileContextFacadeImpl(UserProfileCommandService userProfileCommandService,
                                        UserProfileQueryService userProfileQueryService,
                                        UserProfileRepository userProfileRepository) {
        this.userProfileCommandService = userProfileCommandService;
        this.userProfileQueryService = userProfileQueryService;
        this.userProfileRepository = userProfileRepository;
    }

    @Override
    public Long createProfile(Long userId, String fullName, String phone, String district) {
        var command = new CreateUserProfileCommand(userId, fullName, phone, district);
        var profile = userProfileCommandService.handle(command);
        return profile.map(p -> p.getId()).orElse(0L);
    }

    @Override
    public Long addPointsAndCaps(Long userId, int points, int caps) {
        var command = new AddPointsCommand(userId, points, caps);
        var profile = userProfileCommandService.handle(command);
        return profile.map(p -> p.getId()).orElse(0L);
    }

    @Override
    public Long subtractPoints(Long userId, int points) {
        var command = new SubtractPointsCommand(userId, points);
        var profile = userProfileCommandService.handle(command);
        return profile.map(p -> p.getId()).orElse(0L);
    }

    @Override
    public Boolean existsByUserId(Long userId) {
        return userProfileQueryService.handle(new GetUserProfileByUserIdQuery(userId)).isPresent();
    }

    @Override
    public Integer fetchTotalPointsByUserId(Long userId) {
        return userProfileQueryService.handle(new GetUserProfileByUserIdQuery(userId))
                .map(p -> p.getTotalPoints())
                .orElse(0);
    }

    @Override
    public long countUsers() {
        return userProfileRepository.count();
    }
}

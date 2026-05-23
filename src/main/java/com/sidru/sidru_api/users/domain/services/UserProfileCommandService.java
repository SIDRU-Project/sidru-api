package com.sidru.sidru_api.users.domain.services;

import com.sidru.sidru_api.users.domain.model.aggregates.UserProfile;
import com.sidru.sidru_api.users.domain.model.commands.AddPointsCommand;
import com.sidru.sidru_api.users.domain.model.commands.CreateUserProfileCommand;
import com.sidru.sidru_api.users.domain.model.commands.SubtractPointsCommand;
import com.sidru.sidru_api.users.domain.model.commands.UpdateUserProfileCommand;

import java.util.Optional;

public interface UserProfileCommandService {
    Optional<UserProfile> handle(CreateUserProfileCommand command);
    Optional<UserProfile> handle(UpdateUserProfileCommand command);
    Optional<UserProfile> handle(AddPointsCommand command);
    Optional<UserProfile> handle(SubtractPointsCommand command);
}

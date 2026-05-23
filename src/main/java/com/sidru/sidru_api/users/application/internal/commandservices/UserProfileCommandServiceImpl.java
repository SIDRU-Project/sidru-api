package com.sidru.sidru_api.users.application.internal.commandservices;

import com.sidru.sidru_api.users.domain.model.aggregates.UserProfile;
import com.sidru.sidru_api.users.domain.model.commands.AddPointsCommand;
import com.sidru.sidru_api.users.domain.model.commands.CreateUserProfileCommand;
import com.sidru.sidru_api.users.domain.model.commands.SubtractPointsCommand;
import com.sidru.sidru_api.users.domain.model.commands.UpdateUserProfileCommand;
import com.sidru.sidru_api.users.domain.model.exceptions.InsufficientPointsException;
import com.sidru.sidru_api.users.domain.model.exceptions.UserProfileNotFoundException;
import com.sidru.sidru_api.users.domain.services.UserProfileCommandService;
import com.sidru.sidru_api.users.infrastructure.persistence.jpa.repositories.UserProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class UserProfileCommandServiceImpl implements UserProfileCommandService {

    private final UserProfileRepository userProfileRepository;

    public UserProfileCommandServiceImpl(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
    }

    @Override
    @Transactional
    public Optional<UserProfile> handle(CreateUserProfileCommand command) {
        if (userProfileRepository.existsByUserId(command.userId())) {
            return userProfileRepository.findByUserId(command.userId());
        }
        var profile = new UserProfile(command.userId(), command.fullName(),
                command.phone(), command.district());
        return Optional.of(userProfileRepository.save(profile));
    }

    @Override
    @Transactional
    public Optional<UserProfile> handle(UpdateUserProfileCommand command) {
        var profile = userProfileRepository.findByUserId(command.userId())
                .orElseThrow(UserProfileNotFoundException::new);
        if (command.fullName() != null) profile.setFullName(command.fullName());
        if (command.phone() != null) profile.setPhone(command.phone());
        if (command.district() != null) profile.setDistrict(command.district());
        return Optional.of(userProfileRepository.save(profile));
    }

    @Override
    @Transactional
    public Optional<UserProfile> handle(AddPointsCommand command) {
        var profile = userProfileRepository.findByUserId(command.userId())
                .orElseThrow(UserProfileNotFoundException::new);
        profile.addPoints(command.points());
        profile.addCaps(command.caps());
        profile.incrementSessions();
        return Optional.of(userProfileRepository.save(profile));
    }

    @Override
    @Transactional
    public Optional<UserProfile> handle(SubtractPointsCommand command) {
        var profile = userProfileRepository.findByUserId(command.userId())
                .orElseThrow(UserProfileNotFoundException::new);
        if (profile.getTotalPoints() < command.points()) {
            throw new InsufficientPointsException();
        }
        profile.subtractPoints(command.points());
        return Optional.of(userProfileRepository.save(profile));
    }
}

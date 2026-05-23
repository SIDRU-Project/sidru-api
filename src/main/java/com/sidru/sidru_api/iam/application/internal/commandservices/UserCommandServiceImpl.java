package com.sidru.sidru_api.iam.application.internal.commandservices;

import com.sidru.sidru_api.iam.application.internal.outboundservices.acl.ExternalProfileService;
import com.sidru.sidru_api.iam.application.internal.outboundservices.hashing.HashingService;
import com.sidru.sidru_api.iam.application.internal.outboundservices.tokens.TokenService;
import com.sidru.sidru_api.iam.domain.model.aggregates.User;
import com.sidru.sidru_api.iam.domain.model.commands.SignInCommand;
import com.sidru.sidru_api.iam.domain.model.commands.SignUpCommand;
import com.sidru.sidru_api.iam.domain.model.exceptions.EmailAlreadyExistsException;
import com.sidru.sidru_api.iam.domain.model.exceptions.InvalidCredentialsException;
import com.sidru.sidru_api.iam.domain.model.exceptions.RoleNotFoundException;
import com.sidru.sidru_api.iam.domain.model.exceptions.UserNotFoundException;
import com.sidru.sidru_api.iam.domain.model.valueobjects.Roles;
import com.sidru.sidru_api.iam.domain.services.UserCommandService;
import com.sidru.sidru_api.iam.infrastructure.persistence.jpa.repositories.RoleRepository;
import com.sidru.sidru_api.iam.infrastructure.persistence.jpa.repositories.UserRepository;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class UserCommandServiceImpl implements UserCommandService {

    private final UserRepository userRepository;
    private final HashingService hashingService;
    private final TokenService tokenService;
    private final RoleRepository roleRepository;
    private final ExternalProfileService externalProfileService;

    public UserCommandServiceImpl(UserRepository userRepository, HashingService hashingService,
                                  TokenService tokenService, RoleRepository roleRepository,
                                  ExternalProfileService externalProfileService) {
        this.userRepository = userRepository;
        this.hashingService = hashingService;
        this.tokenService = tokenService;
        this.roleRepository = roleRepository;
        this.externalProfileService = externalProfileService;
    }

    /**
     * Handle the sign-in command and return the authenticated user with a JWT token.
     * @throws UserNotFoundException if the email is not registered
     * @throws InvalidCredentialsException if the password does not match
     */
    @Override
    public Optional<ImmutablePair<User, String>> handle(SignInCommand command) {
        var user = userRepository.findByEmail(command.email());
        if (user.isEmpty())
            throw new UserNotFoundException();
        if (!hashingService.matches(command.password(), user.get().getPassword()))
            throw new InvalidCredentialsException();

        var token = tokenService.generateToken(user.get().getId());
        return Optional.of(ImmutablePair.of(user.get(), token));
    }

    /**
     * Handle the sign-up command and create a new citizen user with its profile.
     * @throws EmailAlreadyExistsException if the email is already registered
     * @throws RoleNotFoundException if any role does not exist
     */
    @Transactional
    @Override
    public Optional<User> handle(SignUpCommand command) {
        if (userRepository.existsByEmail(command.email()))
            throw new EmailAlreadyExistsException();

        var roles = command.roles().stream()
                .map(role ->
                        roleRepository.findByName(role.getName())
                                .orElseThrow(RoleNotFoundException::new))
                .toList();
        if (roles.isEmpty()) {
            roles = List.of(roleRepository.findByName(Roles.ROLE_CITIZEN)
                    .orElseThrow(RoleNotFoundException::new));
        }

        var user = new User(command.email(), hashingService.encode(command.password()), roles);
        var savedUser = userRepository.save(user);

        externalProfileService.createProfile(
                savedUser.getId(),
                command.fullName(),
                command.phone(),
                command.district());

        return userRepository.findByEmail(command.email());
    }
}

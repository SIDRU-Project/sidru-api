package com.sidru.sidru_api.iam.application.internal.commandservices;

import com.sidru.sidru_api.iam.application.internal.outboundservices.acl.ExternalProfileService;
import com.sidru.sidru_api.iam.application.internal.outboundservices.hashing.HashingService;
import com.sidru.sidru_api.iam.application.internal.outboundservices.social.IdTokenVerifier;
import com.sidru.sidru_api.iam.application.internal.outboundservices.social.VerifiedIdentity;
import com.sidru.sidru_api.iam.application.internal.outboundservices.tokens.TokenService;
import com.sidru.sidru_api.iam.domain.model.aggregates.User;
import com.sidru.sidru_api.iam.domain.model.commands.SignInCommand;
import com.sidru.sidru_api.iam.domain.model.commands.SignUpCommand;
import com.sidru.sidru_api.iam.domain.model.commands.SocialSignInCommand;
import com.sidru.sidru_api.iam.domain.model.entities.SocialIdentity;
import com.sidru.sidru_api.iam.domain.model.exceptions.EmailAlreadyExistsException;
import com.sidru.sidru_api.iam.domain.model.exceptions.EmailNotVerifiedException;
import com.sidru.sidru_api.iam.domain.model.exceptions.InvalidSocialTokenException;
import com.sidru.sidru_api.iam.domain.model.exceptions.InvalidCredentialsException;
import com.sidru.sidru_api.iam.domain.model.exceptions.RoleNotFoundException;
import com.sidru.sidru_api.iam.domain.model.exceptions.SocialAdminNotAllowedException;
import com.sidru.sidru_api.iam.domain.model.exceptions.SocialProviderDisabledException;
import com.sidru.sidru_api.iam.domain.model.exceptions.UserNotFoundException;
import com.sidru.sidru_api.iam.domain.model.valueobjects.Roles;
import com.sidru.sidru_api.iam.domain.model.valueobjects.SocialSignInResult;
import com.sidru.sidru_api.iam.domain.services.UserCommandService;
import com.sidru.sidru_api.iam.infrastructure.persistence.jpa.repositories.RoleRepository;
import com.sidru.sidru_api.iam.infrastructure.persistence.jpa.repositories.SocialIdentityRepository;
import com.sidru.sidru_api.iam.infrastructure.persistence.jpa.repositories.UserRepository;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
public class UserCommandServiceImpl implements UserCommandService {

    private final UserRepository userRepository;
    private final HashingService hashingService;
    private final TokenService tokenService;
    private final RoleRepository roleRepository;
    private final ExternalProfileService externalProfileService;
    private final IdTokenVerifier idTokenVerifier;
    private final SocialIdentityRepository socialIdentityRepository;
    private final TransactionTemplate transactionTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    public UserCommandServiceImpl(UserRepository userRepository, HashingService hashingService,
                                  TokenService tokenService, RoleRepository roleRepository,
                                  ExternalProfileService externalProfileService,
                                  IdTokenVerifier idTokenVerifier,
                                  SocialIdentityRepository socialIdentityRepository,
                                  PlatformTransactionManager transactionManager) {
        this.userRepository = userRepository;
        this.hashingService = hashingService;
        this.tokenService = tokenService;
        this.roleRepository = roleRepository;
        this.externalProfileService = externalProfileService;
        this.idTokenVerifier = idTokenVerifier;
        this.socialIdentityRepository = socialIdentityRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
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

    /**
     * Acceso con Google/Apple (spec sidru-web-social, design 4.2). El ID token se verifica fuera de la
     * transacción (hay red de por medio); solo se usan los claims verificados. Si dos primeros accesos
     * simultáneos chocan con la restricción única, se reintenta una vez: la segunda pasada ya ve la
     * identidad creada por la primera.
     * @throws SocialProviderDisabledException si el proveedor no está configurado
     */
    @Override
    public SocialSignInResult handle(SocialSignInCommand command) {
        if (!idTokenVerifier.isEnabled(command.provider()))
            throw new SocialProviderDisabledException();
        var identity = idTokenVerifier.verify(command.provider(), command.token());
        requireMatchingNonce(command.nonce(), identity.nonce());

        try {
            return transactionTemplate.execute(status -> signInWithIdentity(command, identity));
        } catch (DataIntegrityViolationException e) {
            return transactionTemplate.execute(status -> signInWithIdentity(command, identity));
        }
    }

    /**
     * Si el cliente envió un nonce, el token debe traer el mismo (liga el token a la sesión del navegador
     * que inició el login). Sin nonce en el request no se exige nada. Comparación en tiempo constante.
     */
    private static void requireMatchingNonce(String expected, String actual) {
        if (expected == null) return;
        if (actual == null || !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8))) {
            throw new InvalidSocialTokenException("El nonce del token no coincide con el de la solicitud", null);
        }
    }

    private SocialSignInResult signInWithIdentity(SocialSignInCommand command, VerifiedIdentity identity) {
        var existingIdentity = socialIdentityRepository
                .findByProviderAndSubject(command.provider(), identity.subject());

        User user;
        boolean newUser = false;
        if (existingIdentity.isPresent()) {
            user = userRepository.findById(existingIdentity.get().getUserId())
                    .orElseThrow(UserNotFoundException::new);
            if (isAdmin(user)) throw new SocialAdminNotAllowedException();
        } else {
            // Identidad aún no vinculada: un correo sin verificar nunca crea ni vincula nada
            // (evita el pre-secuestro: el atacante abriría una cuenta con el correo de otro).
            if (!identity.emailVerified()) throw new EmailNotVerifiedException();
            var byEmail = userRepository.findByEmailIgnoreCase(identity.email());
            if (byEmail.isPresent()) {
                user = byEmail.get();
                if (isAdmin(user)) throw new SocialAdminNotAllowedException();
            } else {
                user = createSocialUser(command, identity);
                newUser = true;
            }
            socialIdentityRepository.save(new SocialIdentity(
                    user.getId(), command.provider(), identity.subject(), identity.email()));
        }

        return new SocialSignInResult(user, tokenService.generateToken(user.getId()), newUser);
    }

    /** Cuenta CITIZEN sin contraseña conocida (D-WS-4): se guarda el hash de un valor aleatorio. */
    private User createSocialUser(SocialSignInCommand command, VerifiedIdentity identity) {
        var citizen = roleRepository.findByName(Roles.ROLE_CITIZEN).orElseThrow(RoleNotFoundException::new);
        var user = userRepository.save(
                new User(identity.email(), hashingService.encode(randomSecret()), List.of(citizen)));
        externalProfileService.createProfile(user.getId(), displayName(command, identity), null, null);
        return user;
    }

    /** Nombre del perfil: nombre del token, luego el fullName del cliente (Apple) y por último el correo. */
    private static String displayName(SocialSignInCommand command, VerifiedIdentity identity) {
        String name = firstNonBlank(identity.name(), command.fullName());
        if (name == null) name = identity.email().split("@", 2)[0];
        return name.length() > 200 ? name.substring(0, 200) : name;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v.trim();
        }
        return null;
    }

    private String randomSecret() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static boolean isAdmin(User user) {
        return user.getRoles().stream().anyMatch(role -> role.getName() == Roles.ROLE_ADMIN);
    }
}

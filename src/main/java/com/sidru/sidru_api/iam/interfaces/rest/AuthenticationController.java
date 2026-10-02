package com.sidru.sidru_api.iam.interfaces.rest;

import com.sidru.sidru_api.iam.domain.model.commands.SocialSignInCommand;
import com.sidru.sidru_api.iam.domain.model.valueobjects.SocialProvider;
import com.sidru.sidru_api.iam.domain.services.UserCommandService;
import com.sidru.sidru_api.iam.infrastructure.social.SocialAuthProperties;
import com.sidru.sidru_api.iam.interfaces.rest.resources.AppleSignInResource;
import com.sidru.sidru_api.iam.interfaces.rest.resources.AuthProvidersResource;
import com.sidru.sidru_api.iam.interfaces.rest.resources.AuthenticatedUserResource;
import com.sidru.sidru_api.iam.interfaces.rest.resources.GoogleSignInResource;
import com.sidru.sidru_api.iam.interfaces.rest.resources.SignInResource;
import com.sidru.sidru_api.iam.interfaces.rest.resources.SignUpResource;
import com.sidru.sidru_api.iam.interfaces.rest.resources.UserResource;
import com.sidru.sidru_api.iam.interfaces.rest.transform.AuthenticatedUserResourceFromEntityAssembler;
import com.sidru.sidru_api.iam.interfaces.rest.transform.SignInCommandFromResourceAssembler;
import com.sidru.sidru_api.iam.interfaces.rest.transform.SignUpCommandFromResourceAssembler;
import com.sidru.sidru_api.iam.interfaces.rest.transform.UserResourceFromEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/authentication", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Authentication", description = "Authentication Endpoints")
public class AuthenticationController {

    private final UserCommandService userCommandService;
    private final SocialAuthProperties socialAuthProperties;

    public AuthenticationController(UserCommandService userCommandService,
                                    SocialAuthProperties socialAuthProperties) {
        this.userCommandService = userCommandService;
        this.socialAuthProperties = socialAuthProperties;
    }

    @PostMapping("/sign-in")
    public ResponseEntity<AuthenticatedUserResource> signIn(
            @Valid @RequestBody SignInResource signInResource) {

        var signInCommand = SignInCommandFromResourceAssembler.toCommandFromResource(signInResource);
        var authenticatedUser = userCommandService.handle(signInCommand);
        if (authenticatedUser.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        var authenticatedUserResource = AuthenticatedUserResourceFromEntityAssembler
                .toResourceFromEntity(authenticatedUser.get().getLeft(), authenticatedUser.get().getRight());
        return ResponseEntity.ok(authenticatedUserResource);
    }

    @PostMapping("/sign-up")
    public ResponseEntity<UserResource> signUp(@Valid @RequestBody SignUpResource signUpResource) {
        var signUpCommand = SignUpCommandFromResourceAssembler.toCommandFromResource(signUpResource);
        var user = userCommandService.handle(signUpCommand);
        if (user.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        var userResource = UserResourceFromEntityAssembler.toResourceFromEntity(user.get());
        return new ResponseEntity<>(userResource, HttpStatus.CREATED);
    }

    @Operation(summary = "Métodos de acceso disponibles",
            description = "Qué botones muestra la app y con qué client ID. Un proveedor sin configurar va en null.")
    @GetMapping("/providers")
    public ResponseEntity<AuthProvidersResource> providers() {
        AuthProvidersResource.Google google = null;
        if (socialAuthProperties.isEnabled(SocialProvider.GOOGLE)) {
            google = new AuthProvidersResource.Google(
                    socialAuthProperties.getGoogle().getClientIds().get(0));
        }
        AuthProvidersResource.Apple apple = null;
        if (socialAuthProperties.isEnabled(SocialProvider.APPLE)) {
            apple = new AuthProvidersResource.Apple(
                    socialAuthProperties.getApple().getClientIds().get(0),
                    socialAuthProperties.getApple().getRedirectUri());
        }
        return ResponseEntity.ok(new AuthProvidersResource(true, google, apple));
    }

    @Operation(summary = "Entrar con Google",
            description = "Verifica el ID token de Google y devuelve el JWT de SIDRU. Crea la cuenta la primera vez.")
    @PostMapping("/google")
    public ResponseEntity<AuthenticatedUserResource> google(@Valid @RequestBody GoogleSignInResource resource) {
        return socialSignIn(new SocialSignInCommand(SocialProvider.GOOGLE, resource.idToken(), null));
    }

    @Operation(summary = "Entrar con Apple",
            description = "Verifica el identity token de Apple y devuelve el JWT de SIDRU. fullName solo se usa al crear la cuenta.")
    @PostMapping("/apple")
    public ResponseEntity<AuthenticatedUserResource> apple(@Valid @RequestBody AppleSignInResource resource) {
        return socialSignIn(new SocialSignInCommand(
                SocialProvider.APPLE, resource.identityToken(), resource.fullName()));
    }

    private ResponseEntity<AuthenticatedUserResource> socialSignIn(SocialSignInCommand command) {
        var result = userCommandService.handle(command);
        return ResponseEntity.ok(AuthenticatedUserResourceFromEntityAssembler
                .toResourceFromEntity(result.user(), result.token(), result.newUser()));
    }
}

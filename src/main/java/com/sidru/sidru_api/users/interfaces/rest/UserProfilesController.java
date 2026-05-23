package com.sidru.sidru_api.users.interfaces.rest;

import com.sidru.sidru_api.iam.infrastructure.authorization.sfs.model.UserDetailsImpl;
import com.sidru.sidru_api.users.domain.model.queries.GetAllUserProfilesQuery;
import com.sidru.sidru_api.users.domain.model.queries.GetUserProfileByUserIdQuery;
import com.sidru.sidru_api.users.domain.services.UserProfileCommandService;
import com.sidru.sidru_api.users.domain.services.UserProfileQueryService;
import com.sidru.sidru_api.users.interfaces.rest.resources.UpdateUserProfileResource;
import com.sidru.sidru_api.users.interfaces.rest.resources.UserProfileResource;
import com.sidru.sidru_api.users.interfaces.rest.transform.UpdateUserProfileCommandFromResourceAssembler;
import com.sidru.sidru_api.users.interfaces.rest.transform.UserProfileResourceFromEntityAssembler;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/profiles", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Profiles", description = "User profile management endpoints")
public class UserProfilesController {

    private final UserProfileQueryService userProfileQueryService;
    private final UserProfileCommandService userProfileCommandService;

    public UserProfilesController(UserProfileQueryService userProfileQueryService,
                                  UserProfileCommandService userProfileCommandService) {
        this.userProfileQueryService = userProfileQueryService;
        this.userProfileCommandService = userProfileCommandService;
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResource> getMyProfile(@AuthenticationPrincipal UserDetailsImpl principal) {
        var query = new GetUserProfileByUserIdQuery(principal.getUserId());
        var profile = userProfileQueryService.handle(query);
        if (profile.isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(UserProfileResourceFromEntityAssembler.toResourceFromEntity(profile.get()));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserProfileResource> getByUserId(@PathVariable Long userId) {
        var profile = userProfileQueryService.handle(new GetUserProfileByUserIdQuery(userId));
        if (profile.isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(UserProfileResourceFromEntityAssembler.toResourceFromEntity(profile.get()));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserProfileResource>> getAll() {
        var profiles = userProfileQueryService.handle(new GetAllUserProfilesQuery());
        return ResponseEntity.ok(profiles.stream()
                .map(UserProfileResourceFromEntityAssembler::toResourceFromEntity)
                .toList());
    }

    @PutMapping("/me")
    public ResponseEntity<UserProfileResource> updateMyProfile(
            @AuthenticationPrincipal UserDetailsImpl principal,
            @Valid @RequestBody UpdateUserProfileResource resource) {
        var command = UpdateUserProfileCommandFromResourceAssembler
                .toCommandFromResource(principal.getUserId(), resource);
        var profile = userProfileCommandService.handle(command);
        if (profile.isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(UserProfileResourceFromEntityAssembler.toResourceFromEntity(profile.get()));
    }
}

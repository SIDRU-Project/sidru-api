package com.sidru.sidru_api.iam.interfaces.rest.resources;

/** {@code newUser}: true solo cuando la cuenta se creó en este acceso social (aditivo; /sign-in envía false). */
public record AuthenticatedUserResource(Long id, String email, String token, boolean newUser) {
}

package com.sidru.sidru_api.iam.domain.model.valueobjects;

import com.sidru.sidru_api.iam.domain.model.aggregates.User;

/** Resultado del acceso social: el usuario, el JWT de SIDRU y si la cuenta se creó en este acceso. */
public record SocialSignInResult(User user, String token, boolean newUser) {
}

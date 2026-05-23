package com.sidru.sidru_api.iam.infrastructure.tokens.jwt;

import com.sidru.sidru_api.iam.application.internal.outboundservices.tokens.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;

public interface BearerTokenService extends TokenService {

    /** Extracts the JWT token from the Authorization HTTP header. */
    String getBearerTokenFrom(HttpServletRequest token);

    /** Generates a JWT from a Spring Authentication object. */
    String generateToken(Authentication authentication);
}

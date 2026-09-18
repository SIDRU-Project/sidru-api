package com.sidru.sidru_api.iam.infrastructure.tokens.jwt;

import com.sidru.sidru_api.iam.application.internal.outboundservices.tokens.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;

public interface BearerTokenService extends TokenService {

    /** Extrae el token del header Authorization. */
    String getBearerTokenFrom(HttpServletRequest token);

    String generateToken(Authentication authentication);
}

package com.sidru.sidru_api.iam.infrastructure.authorization.sfs.pipeline;

import com.sidru.sidru_api.iam.infrastructure.authorization.sfs.model.UsernamePasswordAuthenticationTokenBuilder;
import com.sidru.sidru_api.iam.infrastructure.tokens.jwt.BearerTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class BearerAuthorizationRequestFilter extends OncePerRequestFilter {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(BearerAuthorizationRequestFilter.class);

    private final BearerTokenService tokenService;

    @Qualifier("defaultUserDetailsService")
    private final UserDetailsService userDetailsService;

    public BearerAuthorizationRequestFilter(BearerTokenService tokenService,
                                            UserDetailsService userDetailsService) {
        this.tokenService = tokenService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String token = tokenService.getBearerTokenFrom(request);
            if (token != null && tokenService.validateToken(token)) {
                Long userId = tokenService.getUserIdFromToken(token);
                var userDetails = userDetailsService.loadUserByUsername(userId.toString());
                SecurityContextHolder.getContext()
                        .setAuthentication(
                                UsernamePasswordAuthenticationTokenBuilder.build(userDetails, request));
            } else if (token != null) {
                // Token presente pero inválido/expirado. No logueamos el valor del token:
                // sería una fuga de credenciales.
                LOGGER.debug("Bearer token present but invalid or expired");
            }
            // Sin token es normal en endpoints públicos y de dispositivo (X-Device-Api-Key).
        } catch (Exception e) {
            LOGGER.warn("Could not set user authentication from bearer token: {}", e.getMessage());
        }
        filterChain.doFilter(request, response);
    }
}

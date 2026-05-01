package com.sidru.sidru_api.iam.infrastructure.authorization.sfs.configuration;

import com.sidru.sidru_api.iam.infrastructure.authorization.sfs.pipeline.BearerAuthorizationRequestFilter;
import com.sidru.sidru_api.iam.infrastructure.authorization.sfs.pipeline.RateLimitFilter;
import com.sidru.sidru_api.iam.infrastructure.hashing.bcrypt.BCryptHashingService;
import com.sidru.sidru_api.iam.infrastructure.tokens.jwt.BearerTokenService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

@Configuration
@EnableMethodSecurity
public class WebSecurityConfiguration {

    private final UserDetailsService userDetailsService;
    private final BearerTokenService tokenService;
    private final BCryptHashingService hashingService;
    private final AuthenticationEntryPoint unauthorizedRequestHandler;

    // Rate limiting (RNF-07) — overridable via env vars / properties.
    @Value("${sidru.security.rate-limit.requests-per-minute:100}")
    private int rateLimitRequestsPerMinute;
    @Value("${sidru.security.rate-limit.max-auth-failures:5}")
    private int rateLimitMaxAuthFailures;
    @Value("${sidru.security.rate-limit.block-seconds:300}")
    private int rateLimitBlockSeconds;

    @Bean
    public BearerAuthorizationRequestFilter authorizationRequestFilter() {
        return new BearerAuthorizationRequestFilter(tokenService, userDetailsService);
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        var authenticationProvider = new DaoAuthenticationProvider();
        authenticationProvider.setUserDetailsService(userDetailsService);
        authenticationProvider.setPasswordEncoder(hashingService);
        return authenticationProvider;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return hashingService;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.cors(corsConfigurer -> corsConfigurer.configurationSource(request -> {
            var cors = new CorsConfiguration();
            cors.setAllowedOrigins(List.of("*"));
            cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
            cors.setAllowedHeaders(List.of("*"));
            return cors;
        }));
        http.csrf(csrfConfigurer -> csrfConfigurer.disable())
                .exceptionHandling(exceptionHandling ->
                        exceptionHandling.authenticationEntryPoint(unauthorizedRequestHandler))
                .sessionManagement(customizer ->
                        customizer.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        authorizeRequests -> authorizeRequests
                                // Public auth endpoints
                                .requestMatchers("/authentication/**").permitAll()
                                // Smart Bin opens a session via X-Device-Api-Key (no JWT)
                                .requestMatchers(org.springframework.http.HttpMethod.POST, "/sessions").permitAll()
                                // Citizen can preview a session before confirming
                                .requestMatchers(org.springframework.http.HttpMethod.GET, "/sessions/qr/*").permitAll()
                                // OpenAPI / Swagger
                                .requestMatchers(
                                        "/v3/api-docs",
                                        "/v3/api-docs/**",
                                        "/swagger-ui.html",
                                        "/swagger-ui/**",
                                        "/swagger-resources/**",
                                        "/webjars/**").permitAll()
                                .anyRequest().authenticated());
        http.authenticationProvider(authenticationProvider());
        http.addFilterBefore(authorizationRequestFilter(), UsernamePasswordAuthenticationFilter.class);
        // Rate limiting runs first, before JWT validation (RNF-07).
        http.addFilterBefore(
                new RateLimitFilter(rateLimitRequestsPerMinute, rateLimitMaxAuthFailures, rateLimitBlockSeconds),
                BearerAuthorizationRequestFilter.class);
        return http.build();
    }

    public WebSecurityConfiguration(
            @Qualifier("defaultUserDetailsService") UserDetailsService userDetailsService,
            BearerTokenService tokenService,
            BCryptHashingService hashingService,
            AuthenticationEntryPoint authenticationEntryPoint) {
        this.userDetailsService = userDetailsService;
        this.tokenService = tokenService;
        this.hashingService = hashingService;
        this.unauthorizedRequestHandler = authenticationEntryPoint;
    }
}

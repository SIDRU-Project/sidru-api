package com.sidru.sidru_api.iam.infrastructure.hashing.bcrypt;

import com.sidru.sidru_api.iam.application.internal.outboundservices.hashing.HashingService;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Marker interface that wires {@link HashingService} (domain port) with Spring's
 * {@link PasswordEncoder} so the same bean can be used by Spring Security.
 */
public interface BCryptHashingService extends HashingService, PasswordEncoder {
}

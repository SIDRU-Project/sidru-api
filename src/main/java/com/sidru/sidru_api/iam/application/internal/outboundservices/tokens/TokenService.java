package com.sidru.sidru_api.iam.application.internal.outboundservices.tokens;

public interface TokenService {
    String generateToken(Long userId);
    Long getUserIdFromToken(String token);
    boolean validateToken(String token);
}

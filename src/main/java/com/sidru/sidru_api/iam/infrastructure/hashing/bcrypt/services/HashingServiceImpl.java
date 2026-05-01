package com.sidru.sidru_api.iam.infrastructure.hashing.bcrypt.services;

import com.sidru.sidru_api.iam.infrastructure.hashing.bcrypt.BCryptHashingService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class HashingServiceImpl implements BCryptHashingService {

    /** BCrypt cost factor (RNF-05 requires >= 12). */
    private static final int BCRYPT_STRENGTH = 12;

    private final BCryptPasswordEncoder passwordEncoder;

    HashingServiceImpl() {
        this.passwordEncoder = new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }

    @Override
    public String encode(CharSequence rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }
}

package com.sidru.sidru_api.iam.infrastructure.authorization.sfs.services;

import com.sidru.sidru_api.iam.infrastructure.authorization.sfs.model.UserDetailsImpl;
import com.sidru.sidru_api.iam.infrastructure.persistence.jpa.repositories.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service(value = "defaultUserDetailsService")
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String userId) throws UsernameNotFoundException {
        Long userIdLong = Long.valueOf(userId);
        var user = userRepository.findById(userIdLong)
                .orElseThrow(
                        () -> new UsernameNotFoundException("User not found with userId: " + userId));
        return UserDetailsImpl.build(user);
    }
}

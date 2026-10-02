package com.sidru.sidru_api.iam.infrastructure.persistence.jpa.repositories;

import com.sidru.sidru_api.iam.domain.model.entities.SocialIdentity;
import com.sidru.sidru_api.iam.domain.model.valueobjects.SocialProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SocialIdentityRepository extends JpaRepository<SocialIdentity, Long> {
    Optional<SocialIdentity> findByProviderAndSubject(SocialProvider provider, String subject);
}

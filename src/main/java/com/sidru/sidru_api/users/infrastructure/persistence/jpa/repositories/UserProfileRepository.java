package com.sidru.sidru_api.users.infrastructure.persistence.jpa.repositories;

import com.sidru.sidru_api.users.domain.model.aggregates.UserProfile;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {
    Optional<UserProfile> findByUserId(Long userId);
    boolean existsByUserId(Long userId);

    /**
     * Lectura con lock pesimista sobre el perfil (RN-BC-07 / US-MN-05): serializa el débito
     * de un retiro para que dos peticiones concurrentes del mismo usuario no pasen ambas el
     * guard de "un retiro a la vez".
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM UserProfile p WHERE p.userId = :userId")
    Optional<UserProfile> findByUserIdForUpdate(@Param("userId") Long userId);
}

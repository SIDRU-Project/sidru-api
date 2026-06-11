package com.sidru.sidru_api.blockchain.infrastructure.persistence.jpa.repositories;

import com.sidru.sidru_api.blockchain.domain.model.aggregates.UserWalletAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserWalletAddressRepository extends JpaRepository<UserWalletAddress, Long> {
    Optional<UserWalletAddress> findByUserId(Long userId);
}

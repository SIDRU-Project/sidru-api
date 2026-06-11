package com.sidru.sidru_api.blockchain.domain.model.aggregates;

import com.sidru.sidru_api.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Additive aggregate that stores the deterministic custodial EVM address derived
 * for a citizen (HD wallet, derivation index = userId). Only the public address is
 * persisted; private keys are never stored. Does not modify the iam/users contexts.
 */
@Getter
@Setter
@Entity
@Table(name = "user_wallet_addresses")
public class UserWalletAddress extends AuditableAbstractAggregateRoot<UserWalletAddress> {

    @Column(name = "user_id", unique = true, nullable = false)
    private Long userId;

    /** Public custodial address (EIP-55 checksum), e.g. 0x1234...abcd. */
    @Column(nullable = false, length = 64)
    private String address;

    /** HD derivation index (equals userId): path m/44'/60'/0'/0/{userId}. */
    @Column(name = "derivation_index", nullable = false)
    private Long derivationIndex;

    public UserWalletAddress() {}

    public UserWalletAddress(Long userId, String address, Long derivationIndex) {
        this.userId = userId;
        this.address = address;
        this.derivationIndex = derivationIndex;
    }
}

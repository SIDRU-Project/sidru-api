package com.sidru.sidru_api.blockchain.application.internal.custodial;

import com.sidru.sidru_api.blockchain.domain.model.aggregates.UserWalletAddress;
import com.sidru.sidru_api.blockchain.infrastructure.persistence.jpa.repositories.UserWalletAddressRepository;
import com.sidru.sidru_api.blockchain.infrastructure.web3j.config.BlockchainProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Bip32ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.crypto.MnemonicUtils;

/**
 * Resolves the deterministic custodial EVM address for a citizen.
 *
 * <p>Derivation: BIP-39 seed from {@code WALLET_MASTER_SEED} mnemonic, then BIP-32
 * path {@code m/44'/60'/0'/0/{userId}}. Only the public address is persisted and used
 * (for minting); the backend signs with its own key, never with the derived key. The
 * seed and derived private keys are never logged.
 *
 * <p>Idempotent: once an address exists for a userId it is reused.
 */
@Service
public class CustodialWalletService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CustodialWalletService.class);

    private final UserWalletAddressRepository repository;
    private final BlockchainProperties properties;

    public CustodialWalletService(UserWalletAddressRepository repository,
                                  BlockchainProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    /**
     * Returns the (checksummed) custodial address for the given userId, deriving and
     * persisting it on first use.
     */
    public String addressFor(Long userId) {
        var existing = repository.findByUserId(userId);
        if (existing.isPresent()) {
            return existing.get().getAddress();
        }
        String address = deriveAddress(userId);
        UserWalletAddress saved = repository.save(new UserWalletAddress(userId, address, userId));
        LOGGER.info("Derived custodial address for user {} (index {}): {}",
                userId, userId, saved.getAddress());
        return saved.getAddress();
    }

    private String deriveAddress(Long userId) {
        String mnemonic = properties.getWalletMasterSeed();
        if (mnemonic == null || mnemonic.isBlank()) {
            throw new IllegalStateException(
                    "WALLET_MASTER_SEED is not configured; cannot derive custodial address");
        }
        // BIP-39 seed (no passphrase). Mnemonic itself is never logged.
        byte[] seed = MnemonicUtils.generateSeed(mnemonic, null);
        Bip32ECKeyPair master = Bip32ECKeyPair.generateKeyPair(seed);

        // Path m/44'/60'/0'/0/{userId} — only the index varies by citizen.
        int index = Math.toIntExact(userId);
        int[] path = {
                44 | Bip32ECKeyPair.HARDENED_BIT,
                60 | Bip32ECKeyPair.HARDENED_BIT,
                0 | Bip32ECKeyPair.HARDENED_BIT,
                0,
                index
        };
        Bip32ECKeyPair derived = Bip32ECKeyPair.deriveKeyPair(master, path);
        // Keys.getAddress -> 40 hex chars (no checksum); apply EIP-55 checksum.
        return Keys.toChecksumAddress("0x" + Keys.getAddress(derived));
    }
}

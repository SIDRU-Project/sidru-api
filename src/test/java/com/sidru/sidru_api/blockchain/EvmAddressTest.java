package com.sidru.sidru_api.blockchain;

import com.sidru.sidru_api.blockchain.domain.model.valueobjects.EvmAddress;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EIP-55 validation (RN-BC-06). Network-free, DB-free.
 */
class EvmAddressTest {

    // Canonical EIP-55 checksummed address (from the EIP-55 spec examples).
    private static final String VALID_CHECKSUM = "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed";

    @Test
    void acceptsValidChecksumAddress() {
        assertTrue(EvmAddress.isValid(VALID_CHECKSUM));
    }

    @Test
    void acceptsAllLowercaseAddress() {
        assertTrue(EvmAddress.isValid(VALID_CHECKSUM.toLowerCase()));
    }

    @Test
    void rejectsBadChecksum() {
        // Flip one char's case to break the checksum.
        String bad = "0x5aAeb6053f3E94C9b9A09f33669435E7Ef1BeAed";
        assertFalse(EvmAddress.isValid(bad));
    }

    @Test
    void rejectsMalformedAddress() {
        assertFalse(EvmAddress.isValid("0x1234"));
        assertFalse(EvmAddress.isValid(null));
        assertFalse(EvmAddress.isValid("not-an-address"));
    }
}

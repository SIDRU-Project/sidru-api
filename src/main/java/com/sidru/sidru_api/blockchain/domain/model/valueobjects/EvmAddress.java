package com.sidru.sidru_api.blockchain.domain.model.valueobjects;

import org.web3j.crypto.Keys;
import org.web3j.crypto.WalletUtils;

/**
 * EVM address validation helper (format + EIP-55 checksum). Pure, network-free.
 */
public final class EvmAddress {

    private EvmAddress() {}

    /**
     * @return true if {@code address} has valid format and, when mixed-case, a valid
     * EIP-55 checksum. All-lowercase/all-uppercase addresses are accepted (checksum
     * not enforceable), matching the client behavior; the value is still well-formed.
     */
    public static boolean isValid(String address) {
        if (address == null || !WalletUtils.isValidAddress(address)) {
            return false;
        }
        String body = address.startsWith("0x") || address.startsWith("0X")
                ? address.substring(2) : address;
        boolean hasUpper = !body.equals(body.toLowerCase());
        boolean hasLower = !body.equals(body.toUpperCase());
        if (hasUpper && hasLower) {
            return Keys.toChecksumAddress(address).equals(address);
        }
        return true;
    }
}

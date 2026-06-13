package com.sidru.sidru_api.blockchain.infrastructure.web3j.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Typed access to sidru.blockchain.* configuration. Values are externalized via
 * environment variables (${VAR:default}) in application.properties. Secrets
 * (private-key, wallet-master-seed) are never logged.
 */
@Component
@ConfigurationProperties(prefix = "sidru.blockchain")
public class BlockchainProperties {

    private boolean enabled;
    private String nodeUrl;
    private String contractAddress;
    private String privateKey;
    private String walletMasterSeed;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getNodeUrl() {
        return nodeUrl;
    }

    public void setNodeUrl(String nodeUrl) {
        this.nodeUrl = nodeUrl;
    }

    public String getContractAddress() {
        return contractAddress;
    }

    public void setContractAddress(String contractAddress) {
        this.contractAddress = contractAddress;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    public String getWalletMasterSeed() {
        return walletMasterSeed;
    }

    public void setWalletMasterSeed(String walletMasterSeed) {
        this.walletMasterSeed = walletMasterSeed;
    }
}

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

    /**
     * Etiqueta de la red que se expone a la app y se persiste en cada transaccion
     * (p. ej. "polygon-amoy" o "polygon"). Debe cambiar junto con node-url y
     * contract-address al mover el despliegue de testnet a mainnet.
     */
    private String networkLabel;

    /** Base del explorador de bloques, sin barra final (p. ej. https://polygonscan.com). */
    private String explorerBaseUrl;

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

    public String getNetworkLabel() {
        return networkLabel;
    }

    public void setNetworkLabel(String networkLabel) {
        this.networkLabel = networkLabel;
    }

    public String getExplorerBaseUrl() {
        return explorerBaseUrl;
    }

    public void setExplorerBaseUrl(String explorerBaseUrl) {
        this.explorerBaseUrl = explorerBaseUrl;
    }

    /** URL del explorador para una transaccion concreta. */
    public String explorerTxUrl(String txHash) {
        if (txHash == null || explorerBaseUrl == null) return null;
        String base = explorerBaseUrl.endsWith("/")
                ? explorerBaseUrl.substring(0, explorerBaseUrl.length() - 1)
                : explorerBaseUrl;
        return base + "/tx/" + txHash;
    }
}

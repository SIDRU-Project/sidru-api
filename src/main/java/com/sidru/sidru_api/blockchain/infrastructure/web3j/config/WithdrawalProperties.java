package com.sidru.sidru_api.blockchain.infrastructure.web3j.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Typed access to sidru.withdrawal.* configuration (spec sidru-mainnet, design.md §9).
 */
@Component
@ConfigurationProperties(prefix = "sidru.withdrawal")
public class WithdrawalProperties {

    private boolean enabled;
    private int minPoints;
    private int maxAttempts;
    private final Reconciliation reconciliation = new Reconciliation();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMinPoints() {
        return minPoints;
    }

    public void setMinPoints(int minPoints) {
        this.minPoints = minPoints;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Reconciliation getReconciliation() {
        return reconciliation;
    }

    public static class Reconciliation {
        private int graceSeconds;
        private long intervalMs;

        public int getGraceSeconds() {
            return graceSeconds;
        }

        public void setGraceSeconds(int graceSeconds) {
            this.graceSeconds = graceSeconds;
        }

        public long getIntervalMs() {
            return intervalMs;
        }

        public void setIntervalMs(long intervalMs) {
            this.intervalMs = intervalMs;
        }
    }
}

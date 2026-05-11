package com.workforcehub.im.websocket;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * IM WebSocket outbox dispatcher 配置。
 */
@ConfigurationProperties(prefix = "workforce.im.outbox.dispatcher")
public class ImWebSocketOutboxProperties {

    private boolean enabled = true;
    private int batchSize = 100;
    private int maxAttempts = 5;
    private long retryDelayMs = 5000L;
    private long sendingStaleMs = 60000L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public long getRetryDelayMs() {
        return retryDelayMs;
    }

    public void setRetryDelayMs(long retryDelayMs) {
        this.retryDelayMs = retryDelayMs;
    }

    public long getSendingStaleMs() {
        return sendingStaleMs;
    }

    public void setSendingStaleMs(long sendingStaleMs) {
        this.sendingStaleMs = sendingStaleMs;
    }
}

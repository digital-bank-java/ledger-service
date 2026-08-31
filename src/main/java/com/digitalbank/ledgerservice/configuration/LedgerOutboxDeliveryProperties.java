package com.digitalbank.ledgerservice.configuration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ledger.outbox.delivery")
public class LedgerOutboxDeliveryProperties {

    private boolean enabled;
    private int batchSize = 25;
    private int maxAttempts = 5;
    private Duration leaseDuration = Duration.ofMinutes(1);
    private Duration retryDelay = Duration.ofSeconds(5);
    private Duration pollDelay = Duration.ofSeconds(5);
    private Duration publishTimeout = Duration.ofSeconds(10);

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public Duration getLeaseDuration() { return leaseDuration; }
    public void setLeaseDuration(Duration leaseDuration) { this.leaseDuration = leaseDuration; }
    public Duration getRetryDelay() { return retryDelay; }
    public void setRetryDelay(Duration retryDelay) { this.retryDelay = retryDelay; }
    public Duration getPollDelay() { return pollDelay; }
    public void setPollDelay(Duration pollDelay) { this.pollDelay = pollDelay; }
    public Duration getPublishTimeout() { return publishTimeout; }
    public void setPublishTimeout(Duration publishTimeout) { this.publishTimeout = publishTimeout; }
}

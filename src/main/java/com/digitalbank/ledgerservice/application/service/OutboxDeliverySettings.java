package com.digitalbank.ledgerservice.application.service;

import java.time.Duration;

public record OutboxDeliverySettings(int batchSize, int maxAttempts, Duration leaseDuration, Duration retryDelay) {

    public OutboxDeliverySettings {
        if (batchSize < 1 || maxAttempts < 1) {
            throw new IllegalArgumentException("outbox delivery batch size and maximum attempts must be positive");
        }
        if (leaseDuration.isNegative() || leaseDuration.isZero() || retryDelay.isNegative()) {
            throw new IllegalArgumentException("outbox delivery durations must not be negative or zero leases");
        }
    }
}

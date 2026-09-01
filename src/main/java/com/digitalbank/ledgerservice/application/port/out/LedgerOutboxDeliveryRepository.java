package com.digitalbank.ledgerservice.application.port.out;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public interface LedgerOutboxDeliveryRepository {

    List<ClaimedOutboxEvent> claimAvailable(Instant now, int batchSize, Duration leaseDuration);

    void markPublished(ClaimedOutboxEvent event, Instant publishedAt);

    void markRetry(ClaimedOutboxEvent event, Instant nextAttemptAt, String error);

    void markQuarantined(ClaimedOutboxEvent event, Instant quarantinedAt, String error);
}

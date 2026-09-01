package com.digitalbank.ledgerservice.application.service;

import com.digitalbank.ledgerservice.application.port.out.LedgerOutboxDeliveryRepository;
import com.digitalbank.ledgerservice.application.port.out.OutboxEventDeliveryTransport;
import java.time.Clock;
import java.time.Instant;

public class OutboxDeliveryService {

    private final LedgerOutboxDeliveryRepository repository;
    private final OutboxEventDeliveryTransport transport;
    private final Clock clock;
    private final OutboxDeliverySettings settings;

    public OutboxDeliveryService(
            LedgerOutboxDeliveryRepository repository,
            OutboxEventDeliveryTransport transport,
            Clock clock,
            OutboxDeliverySettings settings) {
        this.repository = repository;
        this.transport = transport;
        this.clock = clock;
        this.settings = settings;
    }

    public void deliverPendingEvents() {
        var now = clock.instant();
        for (var event : repository.claimAvailable(now, settings.batchSize(), settings.leaseDuration())) {
            deliver(event);
        }
    }

    private void deliver(com.digitalbank.ledgerservice.application.port.out.ClaimedOutboxEvent event) {
        try {
            transport.deliver(event);
            repository.markPublished(event, clock.instant());
        } catch (RuntimeException exception) {
            var now = clock.instant();
            var error = sanitize(exception);
            if (event.attempt() >= settings.maxAttempts()) {
                repository.markQuarantined(event, now, error);
            } else {
                repository.markRetry(event, now.plus(settings.retryDelay()), error);
            }
        }
    }

    private static String sanitize(RuntimeException exception) {
        var message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message.replaceAll("[\\r\\n\\t]", " ").substring(0, Math.min(message.length(), 1000));
    }
}

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
        if (event.attempt() > settings.maxAttempts()) {
            try {
                repository.markQuarantined(
                        event, clock.instant(), "Maximum outbox delivery attempts exceeded");
            } catch (IllegalStateException ignored) {
                // A lease can be lost before the attempt limit is recorded.
            }
            return;
        }

        try {
            transport.deliver(event);
        } catch (RuntimeException exception) {
            handleDeliveryFailure(event, exception);
            return;
        }

        try {
            repository.markPublished(event, clock.instant());
        } catch (IllegalStateException ignored) {
            // Another worker owns the event now; the successful transport call must not be retried.
        }
    }

    private void handleDeliveryFailure(
            com.digitalbank.ledgerservice.application.port.out.ClaimedOutboxEvent event,
            RuntimeException exception) {
        var now = clock.instant();
        var error = sanitize(exception);
        try {
            if (event.attempt() >= settings.maxAttempts()) {
                repository.markQuarantined(event, now, error);
            } else {
                repository.markRetry(event, now.plus(settings.retryDelay()), error);
            }
        } catch (IllegalStateException ignored) {
            // A lease can be lost while recording the transport failure; another worker owns the next action.
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

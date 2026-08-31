package com.digitalbank.ledgerservice.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.digitalbank.ledgerservice.application.port.out.ClaimedOutboxEvent;
import com.digitalbank.ledgerservice.application.port.out.LedgerOutboxDeliveryRepository;
import com.digitalbank.ledgerservice.application.port.out.OutboxEventDeliveryTransport;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboxDeliveryServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-31T02:00:00Z");

    @Test
    void quarantinesAnExhaustedDeliveryWithoutChangingEventIdentity() {
        var event = claimedEvent(3);
        var repository = new RecordingDeliveryRepository(event);
        var transport = new FailingTransport();
        var service = new OutboxDeliveryService(
                repository,
                transport,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new OutboxDeliverySettings(10, 3, Duration.ofMinutes(1), Duration.ofSeconds(5)));

        service.deliverPendingEvents();

        assertThat(transport.eventIds()).containsExactly(event.eventId());
        assertThat(repository.quarantined()).containsExactly(event.eventId());
        assertThat(repository.retried()).isEmpty();
    }

    @Test
    void doesNotRetryWhenTheDeliveryLeaseIsLostAfterTransportSucceeds() {
        var event = claimedEvent(1);
        var repository = new LeaseLostDeliveryRepository(event);
        var service = new OutboxDeliveryService(
                repository,
                ignored -> {},
                Clock.fixed(NOW, ZoneOffset.UTC),
                new OutboxDeliverySettings(10, 3, Duration.ofMinutes(1), Duration.ofSeconds(5)));

        service.deliverPendingEvents();

        assertThat(repository.retried()).isEmpty();
        assertThat(repository.quarantined()).isEmpty();
    }

    private static ClaimedOutboxEvent claimedEvent(int attempt) {
        return new ClaimedOutboxEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                attempt,
                "LedgerPostingCompleted.v1",
                UUID.randomUUID().toString(),
                "posting-001",
                "correlation-001",
                "causation-001",
                "transaction-001",
                "reservation-001",
                "{\"eventId\":\"stable\"}",
                NOW);
    }

    private static final class FailingTransport implements OutboxEventDeliveryTransport {

        private final List<UUID> eventIds = new ArrayList<>();

        @Override
        public void deliver(ClaimedOutboxEvent event) {
            eventIds.add(event.eventId());
            throw new IllegalStateException("broker unavailable");
        }

        List<UUID> eventIds() {
            return List.copyOf(eventIds);
        }
    }

    private static class RecordingDeliveryRepository implements LedgerOutboxDeliveryRepository {

        private final List<ClaimedOutboxEvent> available;
        private final List<UUID> retried = new ArrayList<>();
        private final List<UUID> quarantined = new ArrayList<>();

        RecordingDeliveryRepository(ClaimedOutboxEvent event) {
            this.available = List.of(event);
        }

        @Override
        public List<ClaimedOutboxEvent> claimAvailable(Instant now, int batchSize, Duration leaseDuration) {
            return available;
        }

        @Override
        public void markPublished(ClaimedOutboxEvent event, Instant publishedAt) {
            throw new AssertionError("delivery should have failed");
        }

        @Override
        public void markRetry(ClaimedOutboxEvent event, Instant nextAttemptAt, String error) {
            retried.add(event.eventId());
        }

        @Override
        public void markQuarantined(ClaimedOutboxEvent event, Instant quarantinedAt, String error) {
            quarantined.add(event.eventId());
        }

        List<UUID> retried() {
            return List.copyOf(retried);
        }

        List<UUID> quarantined() {
            return List.copyOf(quarantined);
        }
    }

    private static final class LeaseLostDeliveryRepository extends RecordingDeliveryRepository {

        LeaseLostDeliveryRepository(ClaimedOutboxEvent event) {
            super(event);
        }

        @Override
        public void markPublished(ClaimedOutboxEvent event, Instant publishedAt) {
            throw new IllegalStateException("Outbox delivery lease is no longer owned for event " + event.eventId());
        }
    }
}

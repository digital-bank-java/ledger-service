package com.digitalbank.ledgerservice.adapter.out.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.digitalbank.ledgerservice.application.port.out.ClaimedOutboxEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.UUID;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.SendResult;

class KafkaOutboxEventDeliveryTransportTest {

    @Test
    void publishesGovernedHeadersUsingAggregateIdAsKafkaKey() {
        var recordFactory = new KafkaOutboxEventRecordFactory();
        var eventId = UUID.randomUUID();
        var event = claimedEvent(eventId);

        ProducerRecord<String, String> record = recordFactory.create(event);
        assertThat(record.topic()).isEqualTo("ledger.posting.completed.v1");
        assertThat(record.key()).isEqualTo("posting-aggregate-001");
        assertThat(record.value()).isEqualTo(event.payload());
        assertThat(header(record, "event-id")).isEqualTo(eventId.toString());
        assertThat(header(record, "correlation-id")).isEqualTo("correlation-001");
        assertThat(header(record, "causation-id")).isEqualTo("causation-001");
        assertThat(header(record, "producer")).isEqualTo("ledger-service");
        assertThat(header(record, "schema-version")).isEqualTo("1.0.0");
        assertThat(header(record, "occurred-at")).isEqualTo("2026-08-31T03:00:00Z");
    }

    @Test
    void failsDeliveryWhenKafkaAcknowledgementExceedsConfiguredTimeout() {
        var pendingSend = new CompletableFuture<SendResult<String, String>>();
        var kafkaTemplate = new StubKafkaTemplate(pendingSend);
        var transport = timeoutAwareTransport(kafkaTemplate, Duration.ofMillis(10));

        assertThatThrownBy(() -> transport.deliver(claimedEvent(UUID.randomUUID())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Timed out publishing ledger outbox event")
                .hasCauseInstanceOf(TimeoutException.class);
    }

    @Test
    void propagatesKafkaSendFailuresForOutboxRetryHandling() {
        var kafkaTemplate = new StubKafkaTemplate(
                CompletableFuture.failedFuture(new IllegalStateException("broker rejected")));
        var transport = timeoutAwareTransport(kafkaTemplate, Duration.ofSeconds(1));

        assertThatThrownBy(() -> transport.deliver(claimedEvent(UUID.randomUUID())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unable to publish ledger outbox event")
                .hasRootCauseMessage("broker rejected");
    }

    private static ClaimedOutboxEvent claimedEvent(UUID eventId) {
        return new ClaimedOutboxEvent(
                eventId,
                UUID.randomUUID(),
                1,
                "LedgerPostingCompleted.v1",
                "posting-aggregate-001",
                "posting-request-001",
                "correlation-001",
                "causation-001",
                "transaction-001",
                "reservation-001",
                "{\"eventId\":\"%s\"}".formatted(eventId),
                Instant.parse("2026-08-31T03:00:00Z"));
    }

    private static KafkaOutboxEventDeliveryTransport timeoutAwareTransport(
            KafkaTemplate<String, String> kafkaTemplate, Duration timeout) {
        try {
            var constructor = KafkaOutboxEventDeliveryTransport.class.getDeclaredConstructor(
                    KafkaTemplate.class, Duration.class);
            constructor.setAccessible(true);
            return constructor.newInstance(kafkaTemplate, timeout);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Expected timeout-aware Kafka transport constructor", exception);
        }
    }

    private static String header(ProducerRecord<String, String> record, String key) {
        return new String(record.headers().lastHeader(key).value(), java.nio.charset.StandardCharsets.UTF_8);
    }

    private static final class StubKafkaTemplate extends KafkaTemplate<String, String> {

        private final CompletableFuture<SendResult<String, String>> sendResult;

        StubKafkaTemplate(CompletableFuture<SendResult<String, String>> sendResult) {
            super(new ProducerFactory<>() {
                @Override
                public org.apache.kafka.clients.producer.Producer<String, String> createProducer() {
                    throw new UnsupportedOperationException("test double does not create producers");
                }
            });
            this.sendResult = sendResult;
        }

        @Override
        public CompletableFuture<SendResult<String, String>> send(ProducerRecord<String, String> record) {
            return sendResult;
        }
    }
}

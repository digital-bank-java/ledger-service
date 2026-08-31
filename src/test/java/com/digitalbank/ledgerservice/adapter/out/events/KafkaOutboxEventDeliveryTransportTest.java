package com.digitalbank.ledgerservice.adapter.out.events;

import static org.assertj.core.api.Assertions.assertThat;
import com.digitalbank.ledgerservice.application.port.out.ClaimedOutboxEvent;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;

class KafkaOutboxEventDeliveryTransportTest {

    @Test
    void publishesGovernedHeadersUsingAggregateIdAsKafkaKey() {
        var recordFactory = new KafkaOutboxEventRecordFactory();
        var eventId = UUID.randomUUID();
        var event = new ClaimedOutboxEvent(
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

    private static String header(ProducerRecord<String, String> record, String key) {
        return new String(record.headers().lastHeader(key).value(), java.nio.charset.StandardCharsets.UTF_8);
    }
}

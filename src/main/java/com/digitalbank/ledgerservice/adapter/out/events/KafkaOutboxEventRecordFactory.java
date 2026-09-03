package com.digitalbank.ledgerservice.adapter.out.events;

import com.digitalbank.ledgerservice.application.port.out.ClaimedOutboxEvent;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;

final class KafkaOutboxEventRecordFactory {

    private static final String COMPLETED_EVENT_TYPE = "LedgerPostingCompleted.v1";
    private static final String FAILED_EVENT_TYPE = "LedgerPostingFailed.v1";
    private static final String PRODUCER = "ledger-service";
    private static final String SCHEMA_VERSION = "1.0.0";

    ProducerRecord<String, String> create(ClaimedOutboxEvent event) {
        var record = new ProducerRecord<>(topicFor(event.eventType()), event.aggregateId(), event.payload());
        record.headers().add(new RecordHeader("event-id", bytes(event.eventId().toString())));
        record.headers().add(new RecordHeader("correlation-id", bytes(event.correlationId())));
        record.headers().add(new RecordHeader("causation-id", bytes(event.causationId())));
        record.headers().add(new RecordHeader("producer", bytes(PRODUCER)));
        record.headers().add(new RecordHeader("schema-version", bytes(SCHEMA_VERSION)));
        record.headers().add(new RecordHeader("occurred-at", bytes(event.occurredAt().toString())));
        return record;
    }

    private static String topicFor(String eventType) {
        return switch (eventType) {
            case COMPLETED_EVENT_TYPE -> "ledger.posting.completed.v1";
            case FAILED_EVENT_TYPE -> "ledger.posting.failed.v1";
            default -> throw new IllegalArgumentException("Unsupported ledger outbox event type: " + eventType);
        };
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}

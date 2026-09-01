package com.digitalbank.ledgerservice.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_posting_command_inbox")
class LedgerPostingCommandInboxJpaEntity {

    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "topic", nullable = false, length = 255)
    private String topic;

    @Column(name = "aggregate_id", nullable = false, length = 100)
    private String aggregateId;

    @Column(name = "posting_request_id", nullable = false, length = 100)
    private String postingRequestId;

    @Column(name = "correlation_id", nullable = false, length = 255)
    private String correlationId;

    @Column(name = "causation_id", nullable = false, length = 255)
    private String causationId;

    @Column(name = "producer", nullable = false, length = 100)
    private String producer;

    @Column(name = "schema_version", nullable = false, length = 20)
    private String schemaVersion;

    @Column(name = "transaction_id", nullable = false, length = 255)
    private String transactionId;

    @Column(name = "reservation_request_id", nullable = false, length = 255)
    private String reservationRequestId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "consumed_at", nullable = false)
    private Instant consumedAt;

    protected LedgerPostingCommandInboxJpaEntity() {}

    LedgerPostingCommandInboxJpaEntity(
            UUID eventId,
            String eventType,
            String topic,
            String aggregateId,
            String postingRequestId,
            String correlationId,
            String causationId,
            String producer,
            String schemaVersion,
            String transactionId,
            String reservationRequestId,
            Instant occurredAt,
            Instant consumedAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.topic = topic;
        this.aggregateId = aggregateId;
        this.postingRequestId = postingRequestId;
        this.correlationId = correlationId;
        this.causationId = causationId;
        this.producer = producer;
        this.schemaVersion = schemaVersion;
        this.transactionId = transactionId;
        this.reservationRequestId = reservationRequestId;
        this.occurredAt = occurredAt;
        this.consumedAt = consumedAt;
    }
}

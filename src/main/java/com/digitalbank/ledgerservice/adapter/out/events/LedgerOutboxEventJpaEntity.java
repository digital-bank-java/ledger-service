package com.digitalbank.ledgerservice.adapter.out.events;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ledger_outbox_events")
class LedgerOutboxEventJpaEntity {

    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "aggregate_id", nullable = false, length = 100)
    private String aggregateId;

    @Column(name = "posting_request_id", length = 100)
    private String postingRequestId;

    @Column(name = "correlation_id", nullable = false, length = 255)
    private String correlationId;

    @Column(name = "causation_id", nullable = false, length = 255)
    private String causationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected LedgerOutboxEventJpaEntity() {}

    LedgerOutboxEventJpaEntity(
            UUID eventId,
            String eventType,
            String aggregateId,
            String postingRequestId,
            String correlationId,
            String causationId,
            String payload,
            String status,
            int attempts,
            Instant createdAt,
            Instant publishedAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.postingRequestId = postingRequestId;
        this.correlationId = correlationId;
        this.causationId = causationId;
        this.payload = payload;
        this.status = status;
        this.attempts = attempts;
        this.createdAt = createdAt;
        this.publishedAt = publishedAt;
    }
}

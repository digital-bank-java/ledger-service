package com.digitalbank.ledgerservice.adapter.out.events;

import com.digitalbank.ledgerservice.application.port.out.LedgerEventPublisher;
import com.digitalbank.ledgerservice.domain.model.LedgerEntry;
import com.digitalbank.ledgerservice.domain.model.LedgerEntryLine;
import com.digitalbank.ledgerservice.domain.model.LedgerLineType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class PostgresLedgerEventPublisher implements LedgerEventPublisher {

    private static final String COMPLETED_EVENT_TYPE = "LedgerPostingCompleted.v1";
    private static final String FAILED_EVENT_TYPE = "LedgerPostingFailed.v1";
    private static final String PENDING_STATUS = "PENDING";

    private final SpringDataLedgerOutboxEventRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    PostgresLedgerEventPublisher(SpringDataLedgerOutboxEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public void recordPostingCompleted(
            LedgerEntry entry,
            UUID reversalOfLedgerEntryId,
            String correlationId,
            String causationId,
            Instant occurredAt) {
        var eventId = UUID.randomUUID();
        var payload = new CompletedPayload(
                eventId,
                COMPLETED_EVENT_TYPE,
                occurredAt,
                entry.id().value(),
                correlationId,
                causationId,
                entry.id().value(),
                entry.postingRequestId(),
                reversalOfLedgerEntryId,
                entry.currency(),
                entry.lines().stream().map(PostgresLedgerEventPublisher::toPayloadLine).toList());
        save(
                eventId,
                COMPLETED_EVENT_TYPE,
                entry.id().value().toString(),
                entry.postingRequestId(),
                correlationId,
                causationId,
                payload,
                occurredAt);
    }

    @Override
    public void recordPostingFailed(
            String postingRequestId,
            String failureCode,
            String failureReason,
            String correlationId,
            String causationId,
            Instant occurredAt) {
        var eventId = UUID.randomUUID();
        var payload = new FailedPayload(
                eventId,
                FAILED_EVENT_TYPE,
                occurredAt,
                postingRequestId,
                correlationId,
                causationId,
                postingRequestId,
                failureCode,
                failureReason);
        save(
                eventId,
                FAILED_EVENT_TYPE,
                postingRequestId,
                postingRequestId,
                correlationId,
                causationId,
                payload,
                occurredAt);
    }

    private void save(
            UUID eventId,
            String eventType,
            String aggregateId,
            String postingRequestId,
            String correlationId,
            String causationId,
            Object payload,
            Instant occurredAt) {
        try {
            repository.saveAndFlush(new LedgerOutboxEventJpaEntity(
                    eventId,
                    eventType,
                    aggregateId,
                    postingRequestId,
                    correlationId,
                    causationId,
                    objectMapper.writeValueAsString(payload),
                    PENDING_STATUS,
                    0,
                    occurredAt,
                    null));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize ledger event payload", exception);
        }
    }

    private static PayloadLine toPayloadLine(LedgerEntryLine line) {
        return new PayloadLine(line.accountId(), line.lineType(), line.amount().toPlainString());
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record CompletedPayload(
            UUID eventId,
            String eventType,
            Instant occurredAt,
            UUID aggregateId,
            String correlationId,
            String causationId,
            UUID postingId,
            String postingRequestId,
            UUID reversalOfLedgerEntryId,
            String currency,
            List<PayloadLine> lines) {}

    private record FailedPayload(
            UUID eventId,
            String eventType,
            Instant occurredAt,
            String aggregateId,
            String correlationId,
            String causationId,
            String postingRequestId,
            String failureCode,
            String failureReason) {}

    private record PayloadLine(UUID accountId, LedgerLineType lineType, String amount) {}
}

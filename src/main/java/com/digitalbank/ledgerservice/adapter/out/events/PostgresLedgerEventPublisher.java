package com.digitalbank.ledgerservice.adapter.out.events;

import com.digitalbank.ledgerservice.application.port.out.LedgerEventPublisher;
import com.digitalbank.ledgerservice.domain.model.LedgerEntry;
import com.digitalbank.ledgerservice.domain.model.LedgerEntryLine;
import com.digitalbank.ledgerservice.domain.model.LedgerLineType;
import com.digitalbank.ledgerservice.domain.model.LedgerPostingFailureDecision;
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
    private static final String SCHEMA_VERSION = "1.0.0";
    private static final String PRODUCER = "ledger-service";

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
            String transactionId,
            String reservationRequestId,
            Instant occurredAt) {
        var eventId = UUID.randomUUID();
        var payload = new CompletedPayload(
                eventId,
                COMPLETED_EVENT_TYPE,
                SCHEMA_VERSION,
                PRODUCER,
                occurredAt,
                entry.id().value(),
                correlationId,
                causationId,
                transactionId,
                reservationRequestId,
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
                transactionId,
                reservationRequestId,
                null,
                payload,
                occurredAt);
    }

    @Override
    public void recordPostingFailed(LedgerPostingFailureDecision decision) {
        var payload = new FailedPayload(
                decision.eventId(),
                FAILED_EVENT_TYPE,
                SCHEMA_VERSION,
                PRODUCER,
                decision.occurredAt(),
                decision.postingRequestId(),
                decision.correlationId(),
                decision.causationId(),
                decision.transactionId(),
                decision.reservationRequestId(),
                decision.postingRequestId(),
                decision.failureCode(),
                decision.failureReason());
        save(
                decision.eventId(),
                FAILED_EVENT_TYPE,
                decision.postingRequestId(),
                decision.postingRequestId(),
                decision.correlationId(),
                decision.causationId(),
                decision.transactionId(),
                decision.reservationRequestId(),
                decision.decisionId(),
                payload,
                decision.occurredAt());
    }

    private void save(
            UUID eventId,
            String eventType,
            String aggregateId,
            String postingRequestId,
            String correlationId,
            String causationId,
            String transactionId,
            String reservationRequestId,
            UUID decisionId,
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
                    transactionId,
                    reservationRequestId,
                    decisionId,
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
            String schemaVersion,
            String producer,
            Instant occurredAt,
            UUID aggregateId,
            String correlationId,
            String causationId,
            String transactionId,
            String reservationRequestId,
            UUID postingId,
            String postingRequestId,
            UUID reversalOfLedgerEntryId,
            String currency,
            List<PayloadLine> lines) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record FailedPayload(
            UUID eventId,
            String eventType,
            String schemaVersion,
            String producer,
            Instant occurredAt,
            String aggregateId,
            String correlationId,
            String causationId,
            String transactionId,
            String reservationRequestId,
            String postingRequestId,
            String failureCode,
            String failureReason) {}

    private record PayloadLine(UUID accountId, LedgerLineType lineType, String amount) {}
}

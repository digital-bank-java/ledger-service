package com.digitalbank.ledgerservice.adapter.in.kafka;

import com.digitalbank.ledgerservice.application.port.in.PostLedgerEntryCommand;
import com.digitalbank.ledgerservice.application.port.in.PostLedgerEntryInputPort;
import com.digitalbank.ledgerservice.application.port.in.RecordLedgerPostingFailureCommand;
import com.digitalbank.ledgerservice.application.port.in.RecordLedgerPostingFailureInputPort;
import com.digitalbank.ledgerservice.application.port.out.LedgerEntryRepository;
import com.digitalbank.ledgerservice.application.port.out.LedgerPostingCommandInboxEntry;
import com.digitalbank.ledgerservice.application.port.out.LedgerPostingCommandInboxRepository;
import com.digitalbank.ledgerservice.application.port.out.LedgerPostingFailureDecisionRepository;
import com.digitalbank.ledgerservice.domain.exception.DuplicatePostingRequestException;
import com.digitalbank.ledgerservice.domain.exception.UnbalancedLedgerEntryException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class LedgerPostingRequestedConsumer {

    private static final String EXPECTED_EVENT_TYPE = "LedgerPostingRequested.v1";
    private static final String EXPECTED_SCHEMA_VERSION = "1.0.0";
    private static final String EXPECTED_PRODUCER = "transaction-service";
    private static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    private static final String CONFLICT_ERROR = "CONFLICT";

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final Validator validator;
    private final PostLedgerEntryInputPort postLedgerEntryInputPort;
    private final RecordLedgerPostingFailureInputPort recordLedgerPostingFailureInputPort;
    private final LedgerPostingCommandInboxRepository inboxRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final LedgerPostingFailureDecisionRepository failureDecisionRepository;
    private final Clock clock;
    private final String topic;

    LedgerPostingRequestedConsumer(
            Validator validator,
            PostLedgerEntryInputPort postLedgerEntryInputPort,
            RecordLedgerPostingFailureInputPort recordLedgerPostingFailureInputPort,
            LedgerPostingCommandInboxRepository inboxRepository,
            LedgerEntryRepository ledgerEntryRepository,
            LedgerPostingFailureDecisionRepository failureDecisionRepository,
            Clock clock,
            @Value("${ledger.posting.consumer.topic:ledger.posting.requested.v1}") String topic) {
        this.validator = validator;
        this.postLedgerEntryInputPort = postLedgerEntryInputPort;
        this.recordLedgerPostingFailureInputPort = recordLedgerPostingFailureInputPort;
        this.inboxRepository = inboxRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.failureDecisionRepository = failureDecisionRepository;
        this.clock = clock;
        this.topic = topic;
    }

    @KafkaListener(
            topics = "${ledger.posting.consumer.topic:ledger.posting.requested.v1}",
            groupId = "${ledger.posting.consumer.group-id:ledger-service}",
            autoStartup = "${ledger.posting.consumer.enabled:false}")
    @Transactional
    void consume(ConsumerRecord<String, String> record) {
        var payload = parsePayload(record.value());
        var resolvedEventId = parseUuid(payload.eventId()).orElse(null);
        if (resolvedEventId != null && inboxRepository.existsByEventId(resolvedEventId)) {
            return;
        }

        try {
            validateRecord(record, payload);
            postLedgerEntryInputPort.postLedgerEntry(toCommand(payload));
            saveInbox(payload, record.topic(), resolvedEventId);
        } catch (IllegalArgumentException | ConstraintViolationException | UnbalancedLedgerEntryException exception) {
            recordFailureOrRethrow(payload, VALIDATION_ERROR, exception.getMessage(), exception);
            saveInbox(payload, record.topic(), resolvedEventId);
        } catch (DuplicatePostingRequestException | DataIntegrityViolationException exception) {
            if (!terminalOutcomeExists(payload.postingRequestId())) {
                recordFailureOrRethrow(payload, CONFLICT_ERROR, "Posting request conflicts with existing data", exception);
            }
            saveInbox(payload, record.topic(), resolvedEventId);
        }
    }

    private LedgerPostingRequestedPayload parsePayload(String value) {
        try {
            return objectMapper.readValue(value, LedgerPostingRequestedPayload.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to parse ledger posting command payload", exception);
        }
    }

    private void validateRecord(ConsumerRecord<String, String> record, LedgerPostingRequestedPayload payload) {
        var violations = validator.validate(payload);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        requireEquals("topic", topic, record.topic());
        requireEquals("eventType", EXPECTED_EVENT_TYPE, payload.eventType());
        requireEquals("schemaVersion", EXPECTED_SCHEMA_VERSION, payload.schemaVersion());
        requireEquals("producer", EXPECTED_PRODUCER, payload.producer());
        requireEquals("event-id", payload.eventId(), requiredHeader(record, "event-id"));
        requireEquals("correlation-id", payload.correlationId(), requiredHeader(record, "correlation-id"));
        requireEquals("causation-id", payload.causationId(), requiredHeader(record, "causation-id"));
        requireEquals("producer", payload.producer(), requiredHeader(record, "producer"));
        requireEquals("schema-version", payload.schemaVersion(), requiredHeader(record, "schema-version"));
        requireEquals("occurred-at", payload.occurredAt().toString(), requiredHeader(record, "occurred-at"));
        parseUuid(payload.eventId()).orElseThrow(() -> new IllegalArgumentException("eventId must be a valid UUID"));
    }

    private void recordFailureOrRethrow(
            LedgerPostingRequestedPayload payload, String code, String reason, RuntimeException originalException) {
        if (!canRecordFailure(payload)) {
            throw originalException;
        }
        try {
            recordLedgerPostingFailureInputPort.recordFailure(new RecordLedgerPostingFailureCommand(
                    payload.postingRequestId(),
                    code,
                    reason,
                    payload.correlationId(),
                    payload.causationId(),
                    payload.transactionId(),
                    payload.reservationRequestId()));
        } catch (DuplicatePostingRequestException duplicate) {
            if (!terminalOutcomeExists(payload.postingRequestId())) {
                throw duplicate;
            }
        }
    }

    private boolean terminalOutcomeExists(String postingRequestId) {
        if (postingRequestId == null || postingRequestId.isBlank()) {
            return false;
        }
        return ledgerEntryRepository.findByPostingRequestId(postingRequestId).isPresent()
                || failureDecisionRepository.findByPostingRequestId(postingRequestId).isPresent();
    }

    private void saveInbox(LedgerPostingRequestedPayload payload, String recordTopic, UUID eventId) {
        if (eventId == null) {
            return;
        }
        try {
            inboxRepository.save(new LedgerPostingCommandInboxEntry(
                    eventId,
                    payload.eventType(),
                    recordTopic,
                    payload.aggregateId(),
                    payload.postingRequestId(),
                    payload.correlationId(),
                    payload.causationId(),
                    payload.producer(),
                    payload.schemaVersion(),
                    payload.transactionId(),
                    payload.reservationRequestId(),
                    payload.occurredAt(),
                    clock.instant()));
        } catch (DataIntegrityViolationException ignored) {
            // Concurrent redelivery may race the insert after the posting request uniqueness guard already converged.
        }
    }

    private boolean canRecordFailure(LedgerPostingRequestedPayload payload) {
        return hasText(payload.postingRequestId())
                && hasText(payload.correlationId())
                && hasText(payload.causationId())
                && hasText(payload.transactionId())
                && hasText(payload.reservationRequestId());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static PostLedgerEntryCommand toCommand(LedgerPostingRequestedPayload payload) {
        return new PostLedgerEntryCommand(
                payload.postingRequestId(),
                payload.description(),
                payload.currency(),
                payload.effectiveAt(),
                payload.debitLines().stream()
                        .map(line -> new PostLedgerEntryCommand.Line(line.accountId(), line.amount()))
                        .toList(),
                payload.creditLines().stream()
                        .map(line -> new PostLedgerEntryCommand.Line(line.accountId(), line.amount()))
                        .toList(),
                payload.correlationId(),
                payload.causationId(),
                payload.transactionId(),
                payload.reservationRequestId());
    }

    private static void requireEquals(String field, String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException(field + " must match the governed ledger posting contract");
        }
    }

    private static String requiredHeader(ConsumerRecord<String, String> record, String name) {
        return Optional.ofNullable(record.headers().lastHeader(name))
                .map(Header::value)
                .map(value -> new String(value, StandardCharsets.UTF_8))
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new IllegalArgumentException(name + " header is required"));
    }

    private static Optional<UUID> parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    record LedgerPostingRequestedPayload(
            @NotBlank String eventId,
            @NotBlank String eventType,
            @NotBlank String schemaVersion,
            @NotBlank String producer,
            @NotNull Instant occurredAt,
            @NotBlank String aggregateId,
            @NotBlank String correlationId,
            @NotBlank String causationId,
            @NotBlank String transactionId,
            @NotBlank String reservationRequestId,
            @NotBlank String postingRequestId,
            @NotBlank String description,
            @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
            @NotNull Instant effectiveAt,
            @NotEmpty List<@Valid LinePayload> debitLines,
            @NotEmpty List<@Valid LinePayload> creditLines) {}

    record LinePayload(
            @NotNull UUID accountId,
            @NotNull @DecimalMin("0.0001") @Digits(integer = 15, fraction = 4) BigDecimal amount) {}
}

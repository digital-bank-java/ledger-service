package com.digitalbank.ledgerservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.digitalbank.ledgerservice.application.port.in.PostLedgerEntryCommand;
import com.digitalbank.ledgerservice.application.port.in.RecordLedgerPostingFailureCommand;
import com.digitalbank.ledgerservice.application.port.in.RecordLedgerPostingFailureInputPort;
import com.digitalbank.ledgerservice.application.port.out.LedgerEntryRepository;
import com.digitalbank.ledgerservice.application.port.out.LedgerEventPublisher;
import com.digitalbank.ledgerservice.application.port.out.LedgerOutboxDeliveryRepository;
import com.digitalbank.ledgerservice.application.service.LedgerService;
import com.digitalbank.ledgerservice.domain.model.LedgerEntry;
import com.digitalbank.ledgerservice.domain.model.LedgerEntryId;
import com.digitalbank.ledgerservice.domain.model.LedgerPostingFailureDecision;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class LedgerPersistenceIT {

    private static final Instant FIXED_NOW = Instant.parse("2026-07-03T10:15:30Z");

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RecordLedgerPostingFailureInputPort failureDecisionInputPort;

    @Autowired
    private LedgerOutboxDeliveryRepository outboxDeliveryRepository;

    @Test
    void postsAndLoadsLedgerEntry() throws Exception {
        var debitAccountId = UUID.randomUUID();
        var creditAccountId = UUID.randomUUID();

        var posted = ledgerService.postLedgerEntry(new PostLedgerEntryCommand(
                "ledger-posting-001",
                "Settlement posting",
                "AED",
                Instant.parse("2026-07-03T09:00:00Z"),
                List.of(new PostLedgerEntryCommand.Line(debitAccountId, new BigDecimal("125.50"))),
                List.of(new PostLedgerEntryCommand.Line(creditAccountId, new BigDecimal("125.50"))),
                "correlation-persistence-test",
                "causation-persistence-test",
                "transaction-persistence-test",
                "reservation-persistence-test"));

        var saved = ledgerEntryRepository.findById(new LedgerEntryId(UUID.fromString(posted.ledgerEntryId())));

        assertThat(saved).hasValueSatisfying(entry -> {
            assertThat(entry.postingRequestId()).isEqualTo("ledger-posting-001");
            assertThat(entry.description()).isEqualTo("Settlement posting");
            assertThat(entry.currency()).isEqualTo("AED");
            assertThat(entry.effectiveAt()).isEqualTo(Instant.parse("2026-07-03T09:00:00Z"));
            assertThat(entry.createdAt()).isEqualTo(FIXED_NOW);
            assertThat(entry.totalDebitAmount()).isEqualByComparingTo("125.50");
            assertThat(entry.totalCreditAmount()).isEqualByComparingTo("125.50");
            assertThat(entry.lines()).hasSize(2);
        });

        var outbox = jdbcTemplate.queryForMap(
                "select event_type, aggregate_id, correlation_id, causation_id, transaction_id, reservation_request_id, "
                        + "status, payload::text "
                        + "from ledger_outbox_events where aggregate_id = ?",
                UUID.fromString(posted.ledgerEntryId()).toString());

        assertThat(outbox)
                .containsEntry("event_type", "LedgerPostingCompleted.v1")
                .containsEntry("aggregate_id", posted.ledgerEntryId())
                .containsEntry("correlation_id", "correlation-persistence-test")
                .containsEntry("causation_id", "causation-persistence-test")
                .containsEntry("transaction_id", "transaction-persistence-test")
                .containsEntry("reservation_request_id", "reservation-persistence-test")
                .containsEntry("status", "PENDING");
        var payload = objectMapper.readTree(String.valueOf(outbox.get("payload")));
        assertThat(payload.path("eventType").asText()).isEqualTo("LedgerPostingCompleted.v1");
        assertThat(payload.path("postingId").asText()).isEqualTo(posted.ledgerEntryId());
        assertThat(payload.path("postingRequestId").asText()).isEqualTo("ledger-posting-001");
        assertThat(payload.path("occurredAt").asText()).isEqualTo(FIXED_NOW.toString());
        assertThat(payload.has("reversalOfLedgerEntryId")).isFalse();
        assertThat(payload.path("transactionId").asText()).isEqualTo("transaction-persistence-test");
        assertThat(payload.path("reservationRequestId").asText()).isEqualTo("reservation-persistence-test");
        assertThat(payload.path("lines")).hasSize(2);
        assertThat(payload.path("lines").findValuesAsText("lineType"))
                .containsExactly("DEBIT", "CREDIT");
        assertThat(payload.path("lines").findValuesAsText("amount"))
                .containsExactly("125.50", "125.50");
    }

    @Test
    void persistsRequiredTransactionMetadataWithoutSynthesizingValues() throws Exception {
        var posted = ledgerService.postLedgerEntry(new PostLedgerEntryCommand(
                "ledger-posting-transaction-metadata",
                "Transaction metadata posting",
                "AED",
                Instant.parse("2026-07-03T09:00:00Z"),
                List.of(new PostLedgerEntryCommand.Line(UUID.randomUUID(), new BigDecimal("10.00"))),
                List.of(new PostLedgerEntryCommand.Line(UUID.randomUUID(), new BigDecimal("10.00"))),
                "correlation-transaction-metadata",
                "causation-transaction-metadata",
                "transaction-001",
                "reservation-001"));

        var outbox = jdbcTemplate.queryForMap(
                "select transaction_id, reservation_request_id, payload::text from ledger_outbox_events where aggregate_id = ?",
                posted.ledgerEntryId());
        var payload = objectMapper.readTree(String.valueOf(outbox.get("payload")));

        assertThat(outbox)
                .containsEntry("transaction_id", "transaction-001")
                .containsEntry("reservation_request_id", "reservation-001");
        assertThat(payload.path("transactionId").asText()).isEqualTo("transaction-001");
        assertThat(payload.path("reservationRequestId").asText()).isEqualTo("reservation-001");
        assertThat(payload.path("schemaVersion").asText()).isEqualTo("1.0.0");
        assertThat(payload.path("producer").asText()).isEqualTo("ledger-service");
    }

    @Test
    void persistsTerminalFailureDecisionAndMatchingFailureOutboxEventAtomically() throws Exception {
        var decision = failureDecisionInputPort.recordFailure(new RecordLedgerPostingFailureCommand(
                "ledger-posting-terminal-failure",
                "ACCOUNTING_ERROR",
                "Durable accounting decision rejected the posting",
                "correlation-terminal-failure",
                "causation-terminal-failure",
                "transaction-terminal-failure",
                "reservation-terminal-failure"));

        var outbox = jdbcTemplate.queryForMap(
                "select event_id, decision_id, event_type, transaction_id, reservation_request_id, payload::text "
                        + "from ledger_outbox_events where event_id = ?",
                decision.eventId());

        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from ledger_posting_failure_decisions where decision_id = ?",
                        Integer.class,
                        decision.decisionId()))
                .isEqualTo(1);
        assertThat(outbox)
                .containsEntry("event_id", decision.eventId())
                .containsEntry("decision_id", decision.decisionId())
                .containsEntry("event_type", "LedgerPostingFailed.v1")
                .containsEntry("transaction_id", "transaction-terminal-failure")
                .containsEntry("reservation_request_id", "reservation-terminal-failure");
        var payload = objectMapper.readTree(String.valueOf(outbox.get("payload")));
        assertThat(payload.path("eventId").asText()).isEqualTo(decision.eventId().toString());
        assertThat(payload.path("failureCode").asText()).isEqualTo("ACCOUNTING_ERROR");
    }

    @Test
    void replaysTerminalFailureDecisionWithoutSecondOutboxEvent() {
        var command = new RecordLedgerPostingFailureCommand(
                "ledger-posting-terminal-failure-replay",
                "ACCOUNTING_ERROR",
                "Durable accounting decision rejected the posting",
                "correlation-terminal-failure-replay",
                "causation-terminal-failure-replay",
                "transaction-terminal-failure-replay",
                "reservation-terminal-failure-replay");

        var first = failureDecisionInputPort.recordFailure(command);
        var second = failureDecisionInputPort.recordFailure(command);

        assertThat(second).isEqualTo(first);
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from ledger_posting_failure_decisions where posting_request_id = ?",
                        Integer.class,
                        command.postingRequestId()))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from ledger_outbox_events where posting_request_id = ? "
                                + "and event_type = 'LedgerPostingFailed.v1'",
                        Integer.class,
                        command.postingRequestId()))
                .isEqualTo(1);
    }

    @Test
    void postingRequestCannotProduceBothCompletedAndFailedOutcomes() {
        var postingRequestId = "ledger-posting-cross-outcome";
        ledgerService.postLedgerEntry(new PostLedgerEntryCommand(
                postingRequestId,
                "Cross-outcome posting",
                "AED",
                Instant.parse("2026-07-03T09:00:00Z"),
                List.of(new PostLedgerEntryCommand.Line(UUID.randomUUID(), new BigDecimal("10.00"))),
                List.of(new PostLedgerEntryCommand.Line(UUID.randomUUID(), new BigDecimal("10.00"))),
                "correlation-cross-outcome",
                "causation-cross-outcome",
                "transaction-cross-outcome",
                "reservation-cross-outcome"));

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> failureDecisionInputPort.recordFailure(
                        new RecordLedgerPostingFailureCommand(
                                postingRequestId,
                                "ACCOUNTING_ERROR",
                                "A later failure cannot replace a completed posting",
                                "correlation-cross-outcome-failure",
                                "causation-cross-outcome-failure",
                                "transaction-cross-outcome-failure",
                                "reservation-cross-outcome-failure"))))
                .isInstanceOf(com.digitalbank.ledgerservice.domain.exception.DuplicatePostingRequestException.class);
    }

    @Test
    void legacyOutboxEventWithoutGovernedMetadataIsNotClaimed() {
        var eventId = UUID.randomUUID();
        assertThat(jdbcTemplate.update(
                        "insert into ledger_outbox_events "
                                + "(event_id, event_type, aggregate_id, posting_request_id, correlation_id, "
                                + "causation_id, payload, status, attempts, created_at) "
                                + "values (?, 'LedgerPostingCompleted.v1', ?, ?, ?, ?, '{}'::jsonb, 'PENDING', 0, now())",
                        eventId,
                        UUID.randomUUID().toString(),
                        "legacy-outbox-request",
                        "legacy-correlation",
                        "legacy-causation"))
                .isEqualTo(1);

        assertThat(outboxDeliveryRepository.claimAvailable(
                        Instant.parse("2030-01-01T00:00:00Z"), 100, Duration.ofMinutes(1)))
                .noneMatch(event -> event.eventId().equals(eventId));
    }

    @Test
    void persistsLeaseRetryAndQuarantineWithTheSameEventIdentity() {
        var posted = ledgerService.postLedgerEntry(new PostLedgerEntryCommand(
                "ledger-posting-delivery-state",
                "Delivery lifecycle posting",
                "AED",
                Instant.parse("2026-07-03T09:00:00Z"),
                List.of(new PostLedgerEntryCommand.Line(UUID.randomUUID(), new BigDecimal("10.00"))),
                List.of(new PostLedgerEntryCommand.Line(UUID.randomUUID(), new BigDecimal("10.00"))),
                "correlation-delivery-state",
                "causation-delivery-state",
                "transaction-delivery-state",
                "reservation-delivery-state"));
        var eventId = jdbcTemplate.queryForObject(
                "select event_id from ledger_outbox_events where aggregate_id = ?", UUID.class, posted.ledgerEntryId());
        var initialTime = Instant.parse("2030-01-01T00:00:00Z");

        var firstClaim = outboxDeliveryRepository.claimAvailable(initialTime, 100, Duration.ofMinutes(1)).stream()
                .filter(event -> event.eventId().equals(eventId))
                .findFirst()
                .orElseThrow();
        outboxDeliveryRepository.markRetry(firstClaim, initialTime.plusSeconds(5), "broker unavailable");
        var secondClaim = outboxDeliveryRepository
                .claimAvailable(initialTime.plusSeconds(5), 100, Duration.ofMinutes(1))
                .stream()
                .filter(event -> event.eventId().equals(eventId))
                .findFirst()
                .orElseThrow();
        outboxDeliveryRepository.markQuarantined(secondClaim, initialTime.plusSeconds(6), "broker unavailable");

        var row = jdbcTemplate.queryForMap(
                "select event_id, status, attempts, last_error, quarantined_at from ledger_outbox_events where event_id = ?",
                eventId);
        assertThat(row)
                .containsEntry("event_id", eventId)
                .containsEntry("status", "QUARANTINED")
                .containsEntry("attempts", 2)
                .containsEntry("last_error", "broker unavailable");
        assertThat(row.get("quarantined_at")).isNotNull();
    }

    @Test
    void requeuesQuarantinedEventUsingDocumentedRecoveryUpdate() {
        var posted = ledgerService.postLedgerEntry(new PostLedgerEntryCommand(
                "ledger-posting-delivery-recovery",
                "Delivery recovery posting",
                "AED",
                Instant.parse("2026-07-03T09:00:00Z"),
                List.of(new PostLedgerEntryCommand.Line(UUID.randomUUID(), new BigDecimal("10.00"))),
                List.of(new PostLedgerEntryCommand.Line(UUID.randomUUID(), new BigDecimal("10.00"))),
                "correlation-delivery-recovery",
                "causation-delivery-recovery",
                "transaction-delivery-recovery",
                "reservation-delivery-recovery"));
        var eventId = jdbcTemplate.queryForObject(
                "select event_id from ledger_outbox_events where aggregate_id = ?", UUID.class, posted.ledgerEntryId());
        var initialTime = Instant.parse("2030-01-01T00:00:00Z");

        var claim = outboxDeliveryRepository.claimAvailable(initialTime, 100, Duration.ofMinutes(1)).stream()
                .filter(event -> event.eventId().equals(eventId))
                .findFirst()
                .orElseThrow();
        outboxDeliveryRepository.markQuarantined(claim, initialTime.plusSeconds(1), "broker unavailable");

        assertThat(jdbcTemplate.update(
                        "update ledger_outbox_events set status = 'PENDING', next_attempt_at = now(), "
                                + "lease_id = null, lease_expires_at = null "
                                + "where event_id = ? and status = 'QUARANTINED'",
                        eventId))
                .isEqualTo(1);

        var reclaimed = outboxDeliveryRepository.claimAvailable(initialTime.plusSeconds(2), 100, Duration.ofMinutes(1));
        assertThat(reclaimed).hasSize(1);
        assertThat(reclaimed.getFirst().eventId()).isEqualTo(eventId);
        assertThat(jdbcTemplate.queryForObject(
                        "select quarantined_at from ledger_outbox_events where event_id = ?",
                        Object.class,
                        eventId))
                .isNull();
    }

    @Test
    void persistedLedgerEntriesAreAppendOnly() {
        var debitAccountId = UUID.randomUUID();
        var creditAccountId = UUID.randomUUID();

        var posted = ledgerService.postLedgerEntry(new PostLedgerEntryCommand(
                "ledger-posting-002",
                "Settlement posting",
                "AED",
                Instant.parse("2026-07-03T09:00:00Z"),
                List.of(new PostLedgerEntryCommand.Line(debitAccountId, new BigDecimal("125.50"))),
                List.of(new PostLedgerEntryCommand.Line(creditAccountId, new BigDecimal("125.50"))),
                "correlation-persistence-test",
                "causation-persistence-test",
                "transaction-persistence-test",
                "reservation-persistence-test"));

        var ledgerEntryId = UUID.fromString(posted.ledgerEntryId());

        assertThat(catchThrowableOfType(
                        () -> jdbcTemplate.update(
                                "update ledger_entries set description = ? where id = ?",
                                "Tampered posting",
                                ledgerEntryId),
                        DataAccessException.class))
                .isNotNull();
        assertThat(catchThrowableOfType(
                        () -> jdbcTemplate.update(
                                "update ledger_entry_lines set amount = ? where entry_id = ?",
                                new BigDecimal("999.99"),
                                ledgerEntryId),
                        DataAccessException.class))
                .isNotNull();
        assertThat(catchThrowableOfType(
                        () -> jdbcTemplate.update("delete from ledger_entry_lines where entry_id = ?", ledgerEntryId),
                        DataAccessException.class))
                .isNotNull();
        assertThat(catchThrowableOfType(
                        () -> jdbcTemplate.update("delete from ledger_entries where id = ?", ledgerEntryId),
                        DataAccessException.class))
                .isNotNull();
    }

    @Test
    void deliveryStateCanTransitionButEventFieldsRemainImmutable() {
        var posted = ledgerService.postLedgerEntry(new PostLedgerEntryCommand(
                "ledger-posting-outbox-immutable",
                "Outbox immutable posting",
                "AED",
                Instant.parse("2026-07-03T09:00:00Z"),
                List.of(new PostLedgerEntryCommand.Line(UUID.randomUUID(), new BigDecimal("10.00"))),
                List.of(new PostLedgerEntryCommand.Line(UUID.randomUUID(), new BigDecimal("10.00"))),
                "correlation-outbox-immutable",
                "causation-outbox-immutable",
                "transaction-outbox-immutable",
                "reservation-outbox-immutable"));

        assertThat(jdbcTemplate.update(
                        "update ledger_outbox_events set attempts = attempts + 1 where aggregate_id = ?",
                        posted.ledgerEntryId()))
                .isEqualTo(1);

        assertThat(catchThrowableOfType(
                        () -> jdbcTemplate.update(
                                "update ledger_outbox_events set event_id = ?, event_type = ?, aggregate_id = ?, "
                                        + "posting_request_id = ?, correlation_id = ?, causation_id = ?, payload = '{}'::jsonb, "
                                        + "created_at = now() where aggregate_id = ?",
                                UUID.randomUUID(),
                                "LedgerPostingTampered.v1",
                                "tampered-aggregate",
                                "tampered-request",
                                "tampered-correlation",
                                "tampered-causation",
                                posted.ledgerEntryId()),
                        DataAccessException.class))
                .isNotNull();

        var claim = outboxDeliveryRepository.claimAvailable(
                        Instant.parse("2030-01-01T00:00:00Z"), 100, Duration.ofMinutes(1)).stream()
                .filter(event -> event.aggregateId().equals(posted.ledgerEntryId()))
                .findFirst()
                .orElseThrow();
        outboxDeliveryRepository.markPublished(claim, Instant.parse("2030-01-01T00:00:01Z"));

        assertThat(catchThrowableOfType(
                        () -> jdbcTemplate.update(
                                "update ledger_outbox_events set attempts = attempts + 1 where aggregate_id = ?",
                                posted.ledgerEntryId()),
                        DataAccessException.class))
                .isNotNull();
        var deletionFailure = catchThrowableOfType(
                        () -> jdbcTemplate.update(
                                "delete from ledger_outbox_events where aggregate_id = ?", posted.ledgerEntryId()),
                        DataAccessException.class);
        assertThat(deletionFailure)
                .hasMessageContaining("ledger outbox events are append-only and cannot be deleted");
    }

    @Test
    void databaseRejectsPublishingPendingOutboxEventWithoutAClaimedLease() {
        var posted = ledgerService.postLedgerEntry(new PostLedgerEntryCommand(
                "ledger-posting-invalid-terminal-transition",
                "Invalid terminal transition",
                "AED",
                Instant.parse("2026-07-03T09:00:00Z"),
                List.of(new PostLedgerEntryCommand.Line(UUID.randomUUID(), new BigDecimal("10.00"))),
                List.of(new PostLedgerEntryCommand.Line(UUID.randomUUID(), new BigDecimal("10.00"))),
                "correlation-invalid-terminal-transition",
                "causation-invalid-terminal-transition",
                "transaction-invalid-terminal-transition",
                "reservation-invalid-terminal-transition"));

        assertThat(catchThrowableOfType(
                        () -> jdbcTemplate.update(
                                "update ledger_outbox_events set status = 'PUBLISHED', published_at = now() "
                                        + "where aggregate_id = ?",
                                posted.ledgerEntryId()),
                        DataAccessException.class))
                .isNotNull();
    }

    @Test
    void databaseRejectsDuplicateCompletedEventForLedgerPosting() {
        var posted = ledgerService.postLedgerEntry(new PostLedgerEntryCommand(
                "ledger-posting-outbox-unique",
                "Outbox unique posting",
                "AED",
                Instant.parse("2026-07-03T09:00:00Z"),
                List.of(new PostLedgerEntryCommand.Line(UUID.randomUUID(), new BigDecimal("10.00"))),
                List.of(new PostLedgerEntryCommand.Line(UUID.randomUUID(), new BigDecimal("10.00"))),
                "correlation-outbox-unique",
                "causation-outbox-unique",
                "transaction-outbox-unique",
                "reservation-outbox-unique"));

        assertThat(catchThrowableOfType(
                        () -> jdbcTemplate.update(
                                "insert into ledger_outbox_events "
                                        + "(event_id, event_type, aggregate_id, posting_request_id, correlation_id, "
                                        + "causation_id, payload, status, attempts, created_at) "
                                        + "values (?, 'LedgerPostingCompleted.v1', ?, ?, ?, ?, '{}'::jsonb, 'PENDING', 0, now())",
                                UUID.randomUUID(),
                                posted.ledgerEntryId(),
                                "duplicate-posting-request",
                                "duplicate-correlation",
                                "duplicate-causation"),
                        DataAccessException.class))
                .isNotNull();
    }

    @Test
    void replayDoesNotCreateSecondOutboxEvent() {
        var command = new PostLedgerEntryCommand(
                "ledger-posting-replay",
                "Replay posting",
                "AED",
                Instant.parse("2026-07-03T09:00:00Z"),
                List.of(new PostLedgerEntryCommand.Line(UUID.randomUUID(), new BigDecimal("10.00"))),
                List.of(new PostLedgerEntryCommand.Line(UUID.randomUUID(), new BigDecimal("10.00"))),
                "correlation-replay-test",
                "causation-replay-test",
                "transaction-replay-test",
                "reservation-replay-test");

        var first = ledgerService.postLedgerEntry(command);
        var second = ledgerService.postLedgerEntry(command);

        assertThat(second.replay()).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from ledger_outbox_events where aggregate_id = ?",
                        Integer.class,
                        first.ledgerEntryId()))
                .isEqualTo(1);
    }

    @Test
    void publisherFailureRollsBackLedgerAndOutbox() {
        failingPublisher.failAfterRecording();
        var postingRequestId = "ledger-posting-rollback";

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> ledgerService.postLedgerEntry(new PostLedgerEntryCommand(
                        postingRequestId,
                        "Rollback posting",
                        "AED",
                        Instant.parse("2026-07-03T09:00:00Z"),
                        List.of(new PostLedgerEntryCommand.Line(UUID.randomUUID(), new BigDecimal("10.00"))),
                        List.of(new PostLedgerEntryCommand.Line(UUID.randomUUID(), new BigDecimal("10.00"))),
                        "correlation-rollback-test",
                        "causation-rollback-test",
                        "transaction-rollback-test",
                        "reservation-rollback-test")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from ledger_entries where posting_request_id = ?", Integer.class, postingRequestId))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from ledger_outbox_events where correlation_id = ?",
                        Integer.class,
                        "correlation-rollback-test"))
                .isZero();
    }

    @Test
    void publisherFailureRollsBackFailureDecisionAndFailureOutboxEvent() {
        failingPublisher.failAfterRecording();
        var postingRequestId = "ledger-posting-failure-decision-rollback";

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> failureDecisionInputPort.recordFailure(
                        new RecordLedgerPostingFailureCommand(
                                postingRequestId,
                                "ACCOUNTING_ERROR",
                                "Durable accounting rule rejected the posting",
                                "correlation-failure-decision-rollback",
                                "causation-failure-decision-rollback",
                                "transaction-failure-decision-rollback",
                                "reservation-failure-decision-rollback")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from ledger_posting_failure_decisions where posting_request_id = ?",
                        Integer.class,
                        postingRequestId))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from ledger_outbox_events where posting_request_id = ? and event_type = 'LedgerPostingFailed.v1'",
                        Integer.class,
                        postingRequestId))
                .isZero();
    }

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        }

        @Bean
        @Primary
        FailingAfterRecordingPublisher failingPublisher(
                @Qualifier("postgresLedgerEventPublisher") LedgerEventPublisher delegate) {
            return new FailingAfterRecordingPublisher(delegate);
        }
    }

    @Autowired
    private FailingAfterRecordingPublisher failingPublisher;

    static final class FailingAfterRecordingPublisher implements LedgerEventPublisher {

        private final LedgerEventPublisher delegate;
        private boolean fail;

        FailingAfterRecordingPublisher(LedgerEventPublisher delegate) {
            this.delegate = delegate;
        }

        void failAfterRecording() {
            fail = true;
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
            delegate.recordPostingCompleted(
                    entry,
                    reversalOfLedgerEntryId,
                    correlationId,
                    causationId,
                    transactionId,
                    reservationRequestId,
                    occurredAt);
            failIfConfigured();
        }

        @Override
        public void recordPostingFailed(LedgerPostingFailureDecision decision) {
            delegate.recordPostingFailed(decision);
            failIfConfigured();
        }

        private void failIfConfigured() {
            if (fail) {
                fail = false;
                throw new IllegalStateException("forced publisher failure");
            }
        }
    }
}

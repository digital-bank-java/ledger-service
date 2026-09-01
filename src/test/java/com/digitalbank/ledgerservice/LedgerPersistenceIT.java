package com.digitalbank.ledgerservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.digitalbank.ledgerservice.application.port.in.PostLedgerEntryCommand;
import com.digitalbank.ledgerservice.application.port.out.LedgerEntryRepository;
import com.digitalbank.ledgerservice.application.port.out.LedgerEventPublisher;
import com.digitalbank.ledgerservice.application.service.LedgerService;
import com.digitalbank.ledgerservice.domain.model.LedgerEntry;
import com.digitalbank.ledgerservice.domain.model.LedgerEntryId;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
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
                "causation-persistence-test"));

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
                "select event_type, aggregate_id, correlation_id, causation_id, status, payload::text "
                        + "from ledger_outbox_events where aggregate_id = ?",
                UUID.fromString(posted.ledgerEntryId()).toString());

        assertThat(outbox)
                .containsEntry("event_type", "LedgerPostingCompleted.v1")
                .containsEntry("aggregate_id", posted.ledgerEntryId())
                .containsEntry("correlation_id", "correlation-persistence-test")
                .containsEntry("causation_id", "causation-persistence-test")
                .containsEntry("status", "PENDING");
        var payload = objectMapper.readTree(String.valueOf(outbox.get("payload")));
        assertThat(payload.path("eventType").asText()).isEqualTo("LedgerPostingCompleted.v1");
        assertThat(payload.path("postingId").asText()).isEqualTo(posted.ledgerEntryId());
        assertThat(payload.path("postingRequestId").asText()).isEqualTo("ledger-posting-001");
        assertThat(payload.path("occurredAt").asText()).isEqualTo(FIXED_NOW.toString());
        assertThat(payload.has("reversalOfLedgerEntryId")).isFalse();
        assertThat(payload.path("lines")).hasSize(2);
        assertThat(payload.path("lines").findValuesAsText("lineType"))
                .containsExactly("DEBIT", "CREDIT");
        assertThat(payload.path("lines").findValuesAsText("amount"))
                .containsExactly("125.50", "125.50");
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
                "causation-persistence-test"));

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
                "causation-outbox-immutable"));

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

        assertThat(jdbcTemplate.update(
                        "update ledger_outbox_events set status = 'PUBLISHED', published_at = now() "
                                + "where aggregate_id = ?",
                        posted.ledgerEntryId()))
                .isEqualTo(1);

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
    void databaseRejectsDuplicateCompletedEventForLedgerPosting() {
        var posted = ledgerService.postLedgerEntry(new PostLedgerEntryCommand(
                "ledger-posting-outbox-unique",
                "Outbox unique posting",
                "AED",
                Instant.parse("2026-07-03T09:00:00Z"),
                List.of(new PostLedgerEntryCommand.Line(UUID.randomUUID(), new BigDecimal("10.00"))),
                List.of(new PostLedgerEntryCommand.Line(UUID.randomUUID(), new BigDecimal("10.00"))),
                "correlation-outbox-unique",
                "causation-outbox-unique"));

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
                "causation-replay-test");

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
                        "causation-rollback-test")))
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
                Instant occurredAt) {
            delegate.recordPostingCompleted(entry, reversalOfLedgerEntryId, correlationId, causationId, occurredAt);
            if (fail) {
                fail = false;
                throw new IllegalStateException("forced publisher failure");
            }
        }

        @Override
        public void recordPostingFailed(
                String postingRequestId,
                String failureCode,
                String failureReason,
                String correlationId,
                String causationId,
                Instant occurredAt) {
            delegate.recordPostingFailed(
                    postingRequestId, failureCode, failureReason, correlationId, causationId, occurredAt);
        }
    }
}

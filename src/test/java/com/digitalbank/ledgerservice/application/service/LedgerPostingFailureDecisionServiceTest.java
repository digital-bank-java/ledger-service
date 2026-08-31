package com.digitalbank.ledgerservice.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.digitalbank.ledgerservice.application.port.in.RecordLedgerPostingFailureCommand;
import com.digitalbank.ledgerservice.application.port.out.LedgerEventPublisher;
import com.digitalbank.ledgerservice.application.port.out.LedgerPostingFailureDecisionRepository;
import com.digitalbank.ledgerservice.domain.exception.DuplicatePostingRequestException;
import com.digitalbank.ledgerservice.domain.model.LedgerEntry;
import com.digitalbank.ledgerservice.domain.model.LedgerPostingFailureDecision;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LedgerPostingFailureDecisionServiceTest {

    private final RecordingFailureDecisionRepository repository = new RecordingFailureDecisionRepository();
    private final RecordingLedgerEventPublisher publisher = new RecordingLedgerEventPublisher();
    private final LedgerPostingFailureDecisionService service = new LedgerPostingFailureDecisionService(
            repository,
            publisher,
            Clock.fixed(Instant.parse("2026-08-31T01:00:00Z"), ZoneOffset.UTC));

    @Test
    void recordsOneFailureEventAfterPersistingTerminalBusinessDecision() {
        var decision = service.recordFailure(new RecordLedgerPostingFailureCommand(
                "posting-failure-001",
                "ACCOUNTING_ERROR",
                "Posting rejected by a durable accounting rule",
                "correlation-failure-001",
                "causation-failure-001",
                "transaction-failure-001",
                "reservation-failure-001"));

        assertThat(repository.decisions()).containsExactly(decision);
        assertThat(publisher.failures()).containsExactly(decision);
        assertThat(decision.eventId()).isNotNull();
        assertThat(decision.occurredAt()).isEqualTo(Instant.parse("2026-08-31T01:00:00Z"));
    }

    @Test
    void replaysIdenticalFailureDecisionWithoutPublishingAnotherEvent() {
        var original = new LedgerPostingFailureDecision(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "posting-failure-replay",
                "ACCOUNTING_ERROR",
                "Posting rejected by a durable accounting rule",
                "correlation-failure-replay",
                "causation-failure-replay",
                "transaction-failure-replay",
                "reservation-failure-replay",
                Instant.parse("2026-08-31T00:30:00Z"));
        repository.seed(original);

        var replay = service.recordFailure(new RecordLedgerPostingFailureCommand(
                "posting-failure-replay",
                "ACCOUNTING_ERROR",
                "Posting rejected by a durable accounting rule",
                "correlation-failure-replay",
                "causation-failure-replay",
                "transaction-failure-replay",
                "reservation-failure-replay"));

        assertThat(replay).isEqualTo(original);
        assertThat(repository.decisions()).containsExactly(original);
        assertThat(publisher.failures()).isEmpty();
    }

    @Test
    void rejectsReusedFailurePostingRequestIdWithDifferentDecisionPayload() {
        repository.seed(new LedgerPostingFailureDecision(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "posting-failure-conflict",
                "ACCOUNTING_ERROR",
                "Posting rejected by a durable accounting rule",
                "correlation-failure-conflict",
                "causation-failure-conflict",
                "transaction-failure-conflict",
                "reservation-failure-conflict",
                Instant.parse("2026-08-31T00:30:00Z")));

        assertThatThrownBy(() -> service.recordFailure(new RecordLedgerPostingFailureCommand(
                        "posting-failure-conflict",
                        "ACCOUNTING_ERROR",
                        "Posting rejected by a durable accounting rule",
                        "correlation-failure-conflict",
                        "causation-failure-conflict",
                        "transaction-failure-conflict",
                        "different-reservation-failure-conflict")))
                .isInstanceOf(DuplicatePostingRequestException.class)
                .hasMessageContaining("Posting request already exists: posting-failure-conflict");
        assertThat(repository.decisions()).hasSize(1);
        assertThat(publisher.failures()).isEmpty();
    }

    @Test
    void requiresGovernedFailureEventIdentifiers() {
        assertThatThrownBy(() -> service.recordFailure(new RecordLedgerPostingFailureCommand(
                        "posting-failure-missing-governed-metadata",
                        "ACCOUNTING_ERROR",
                        "Posting rejected by a durable accounting rule",
                        "correlation-failure-required",
                        "causation-failure-required",
                        null,
                        "reservation-failure-required")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("transactionId is required");
        assertThat(repository.decisions()).isEmpty();
        assertThat(publisher.failures()).isEmpty();
    }

    private static final class RecordingFailureDecisionRepository implements LedgerPostingFailureDecisionRepository {

        private final List<LedgerPostingFailureDecision> decisions = new ArrayList<>();

        @Override
        public Optional<LedgerPostingFailureDecision> findByPostingRequestId(String postingRequestId) {
            return decisions.stream()
                    .filter(decision -> decision.postingRequestId().equals(postingRequestId))
                    .findFirst();
        }

        @Override
        public LedgerPostingFailureDecision save(LedgerPostingFailureDecision decision) {
            decisions.add(decision);
            return decision;
        }

        void seed(LedgerPostingFailureDecision decision) {
            decisions.add(decision);
        }

        List<LedgerPostingFailureDecision> decisions() {
            return List.copyOf(decisions);
        }
    }

    private static final class RecordingLedgerEventPublisher implements LedgerEventPublisher {

        private final List<LedgerPostingFailureDecision> failures = new ArrayList<>();

        @Override
        public void recordPostingCompleted(
                LedgerEntry entry,
                UUID reversalOfLedgerEntryId,
                String correlationId,
                String causationId,
                String transactionId,
                String reservationRequestId,
                Instant occurredAt) {}

        @Override
        public void recordPostingFailed(LedgerPostingFailureDecision decision) {
            failures.add(decision);
        }

        List<LedgerPostingFailureDecision> failures() {
            return List.copyOf(failures);
        }
    }
}

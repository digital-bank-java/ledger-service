package com.digitalbank.ledgerservice.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.digitalbank.ledgerservice.application.port.in.RecordLedgerPostingFailureCommand;
import com.digitalbank.ledgerservice.application.port.out.LedgerEventPublisher;
import com.digitalbank.ledgerservice.application.port.out.LedgerPostingFailureDecisionRepository;
import com.digitalbank.ledgerservice.domain.model.LedgerEntry;
import com.digitalbank.ledgerservice.domain.model.LedgerPostingFailureDecision;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
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

    private static final class RecordingFailureDecisionRepository implements LedgerPostingFailureDecisionRepository {

        private final List<LedgerPostingFailureDecision> decisions = new ArrayList<>();

        @Override
        public LedgerPostingFailureDecision save(LedgerPostingFailureDecision decision) {
            decisions.add(decision);
            return decision;
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

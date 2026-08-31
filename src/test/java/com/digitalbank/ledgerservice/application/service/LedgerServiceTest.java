package com.digitalbank.ledgerservice.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.digitalbank.ledgerservice.application.port.in.PostLedgerEntryCommand;
import com.digitalbank.ledgerservice.application.port.in.PostLedgerReversalCommand;
import com.digitalbank.ledgerservice.application.port.out.LedgerEntryRepository;
import com.digitalbank.ledgerservice.application.port.out.LedgerEventPublisher;
import com.digitalbank.ledgerservice.domain.exception.UnbalancedLedgerEntryException;
import com.digitalbank.ledgerservice.domain.model.LedgerEntry;
import com.digitalbank.ledgerservice.domain.model.LedgerEntryId;
import com.digitalbank.ledgerservice.domain.model.LedgerLineType;
import com.digitalbank.ledgerservice.domain.model.LedgerPostingFailureDecision;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LedgerServiceTest {

    private final InMemoryLedgerEntryRepository repository = new InMemoryLedgerEntryRepository();
    private final RecordingLedgerEventPublisher publisher = new RecordingLedgerEventPublisher();
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-03T10:15:30Z"), ZoneOffset.UTC);
    private final LedgerService ledgerService = new LedgerService(repository, publisher, clock);

    @Test
    void postsBalancedLedgerEntry() {
        var command = balancedCommand("ledger-posting-001", new BigDecimal("100.00"), new BigDecimal("100.00"));

        var view = ledgerService.postLedgerEntry(command);

        assertThat(view.ledgerEntryId()).isNotBlank();
        assertThat(view.postingRequestId()).isEqualTo("ledger-posting-001");
        assertThat(view.currency()).isEqualTo("AED");
        assertThat(view.totalDebitAmount()).isEqualByComparingTo("100.00");
        assertThat(view.totalCreditAmount()).isEqualByComparingTo("100.00");
        assertThat(view.createdAt()).isEqualTo(clock.instant());
        assertThat(repository.entries()).hasSize(1);
        assertThat(publisher.completed()).hasSize(1);
        assertThat(publisher.completed().getFirst().entry()).isEqualTo(repository.entries().getFirst());
        assertThat(publisher.completed().getFirst().correlationId()).isEqualTo("correlation-test");
        assertThat(publisher.completed().getFirst().causationId()).isEqualTo("causation-test");
    }

    @Test
    void replaysDuplicatePostingRequestId() {
        var command = balancedCommand("ledger-posting-001", new BigDecimal("100.00"), new BigDecimal("100.00"));
        var first = ledgerService.postLedgerEntry(command);
        var second = ledgerService.postLedgerEntry(command);

        assertThat(second.replay()).isTrue();
        assertThat(second.ledgerEntryId()).isEqualTo(first.ledgerEntryId());
        assertThat(repository.entries()).hasSize(1);
        assertThat(publisher.completed()).hasSize(1);
    }

    @Test
    void rejectsUnbalancedLedgerEntry() {
        var command = balancedCommand("ledger-posting-002", new BigDecimal("100.00"), new BigDecimal("90.00"));

        assertThatThrownBy(() -> ledgerService.postLedgerEntry(command))
                .isInstanceOf(UnbalancedLedgerEntryException.class)
                .hasMessageContaining("total debit amount must equal total credit amount");
        assertThat(publisher.failed()).isEmpty();
    }

    @Test
    void rejectsAmountsWithMoreThanFourDecimalPlaces() {
        var command = balancedCommand("ledger-posting-precision", new BigDecimal("100.00001"), new BigDecimal("100.00001"));

        assertThatThrownBy(() -> ledgerService.postLedgerEntry(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("amount must have no more than 4 decimal places");
        assertThat(repository.entries()).isEmpty();
        assertThat(publisher.completed()).isEmpty();
    }

    @Test
    void treatsDebitAndCreditBucketsAsTheSourceOfLineType() {
        var debitAccountId = UUID.randomUUID();
        var creditAccountId = UUID.randomUUID();
        var command = new PostLedgerEntryCommand(
                "ledger-posting-004",
                "Settlement posting",
                "AED",
                Instant.parse("2026-07-03T09:00:00Z"),
                List.of(new PostLedgerEntryCommand.Line(debitAccountId, new BigDecimal("100.00"))),
                List.of(new PostLedgerEntryCommand.Line(creditAccountId, new BigDecimal("100.00"))),
                "correlation-test",
                "causation-test");

        var view = ledgerService.postLedgerEntry(command);

        assertThat(view.lines())
                .anySatisfy(line -> {
                    assertThat(line.accountId()).isEqualTo(debitAccountId);
                    assertThat(line.lineType()).isEqualTo(LedgerLineType.DEBIT);
                })
                .anySatisfy(line -> {
                    assertThat(line.accountId()).isEqualTo(creditAccountId);
                    assertThat(line.lineType()).isEqualTo(LedgerLineType.CREDIT);
                });
    }

    @Test
    void publishesReversalCompletionWithSourceEntryIdAndMetadata() {
        var source = ledgerService.postLedgerEntry(
                balancedCommand("ledger-posting-source", new BigDecimal("100.00"), new BigDecimal("100.00")));

        var reversal = ledgerService.reverseLedgerEntry(new PostLedgerReversalCommand(
                UUID.fromString(source.ledgerEntryId()),
                "ledger-reversal-001",
                "Reverse posting",
                Instant.parse("2026-07-03T11:00:00Z"),
                "correlation-reversal-test",
                "causation-reversal-test"));

        assertThat(reversal.replay()).isFalse();
        assertThat(publisher.completed()).hasSize(2);
        assertThat(publisher.completed().getLast().reversalOfLedgerEntryId())
                .isEqualTo(UUID.fromString(source.ledgerEntryId()));
        assertThat(publisher.completed().getLast().correlationId()).isEqualTo("correlation-reversal-test");
        assertThat(publisher.completed().getLast().causationId()).isEqualTo("causation-reversal-test");
    }

    private static PostLedgerEntryCommand balancedCommand(
            String postingRequestId, BigDecimal debitAmount, BigDecimal creditAmount) {
        return new PostLedgerEntryCommand(
                postingRequestId,
                "Settlement posting",
                "AED",
                Instant.parse("2026-07-03T09:00:00Z"),
                List.of(new PostLedgerEntryCommand.Line(UUID.randomUUID(), debitAmount)),
                List.of(new PostLedgerEntryCommand.Line(UUID.randomUUID(), creditAmount)),
                "correlation-test",
                "causation-test");
    }

    private static final class RecordingLedgerEventPublisher implements LedgerEventPublisher {

        private final List<CompletedCall> completed = new ArrayList<>();
        private final List<LedgerPostingFailureDecision> failed = new ArrayList<>();

        @Override
        public void recordPostingCompleted(
                LedgerEntry entry,
                UUID reversalOfLedgerEntryId,
                String correlationId,
                String causationId,
                String transactionId,
                String reservationRequestId,
                Instant occurredAt) {
            completed.add(new CompletedCall(
                    entry,
                    reversalOfLedgerEntryId,
                    correlationId,
                    causationId,
                    transactionId,
                    reservationRequestId,
                    occurredAt));
        }

        @Override
        public void recordPostingFailed(LedgerPostingFailureDecision decision) {
            failed.add(decision);
        }

        List<CompletedCall> completed() {
            return List.copyOf(completed);
        }

        List<LedgerPostingFailureDecision> failed() {
            return List.copyOf(failed);
        }
    }

    private record CompletedCall(
            LedgerEntry entry,
            UUID reversalOfLedgerEntryId,
            String correlationId,
            String causationId,
            String transactionId,
            String reservationRequestId,
            Instant occurredAt) {}

    private static final class InMemoryLedgerEntryRepository implements LedgerEntryRepository {

        private final List<LedgerEntry> entries = new ArrayList<>();

        @Override
        public LedgerEntry save(LedgerEntry ledgerEntry) {
            entries.add(ledgerEntry);
            return ledgerEntry;
        }

        @Override
        public Optional<LedgerEntry> findById(LedgerEntryId ledgerEntryId) {
            return entries.stream()
                    .filter(entry -> entry.id().equals(ledgerEntryId))
                    .findFirst();
        }

        @Override
        public boolean existsByPostingRequestId(String postingRequestId) {
            return entries.stream().anyMatch(entry -> entry.postingRequestId().equals(postingRequestId));
        }

        @Override
        public Optional<LedgerEntry> findByPostingRequestId(String postingRequestId) {
            return entries.stream()
                    .filter(entry -> entry.postingRequestId().equals(postingRequestId))
                    .findFirst();
        }

        List<LedgerEntry> entries() {
            return List.copyOf(entries);
        }
    }
}

package com.digitalbank.ledgerservice.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.digitalbank.ledgerservice.application.port.in.PostLedgerEntryCommand;
import com.digitalbank.ledgerservice.application.port.out.LedgerEntryRepository;
import com.digitalbank.ledgerservice.domain.exception.DuplicatePostingRequestException;
import com.digitalbank.ledgerservice.domain.exception.UnbalancedLedgerEntryException;
import com.digitalbank.ledgerservice.domain.model.LedgerEntry;
import com.digitalbank.ledgerservice.domain.model.LedgerEntryId;
import com.digitalbank.ledgerservice.domain.model.LedgerLineType;
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
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-03T10:15:30Z"), ZoneOffset.UTC);
    private final LedgerService ledgerService = new LedgerService(repository, clock);

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
    }

    @Test
    void rejectsDuplicatePostingRequestId() {
        var command = balancedCommand("ledger-posting-001", new BigDecimal("100.00"), new BigDecimal("100.00"));
        ledgerService.postLedgerEntry(command);

        assertThatThrownBy(() -> ledgerService.postLedgerEntry(command))
                .isInstanceOf(DuplicatePostingRequestException.class);
    }

    @Test
    void rejectsUnbalancedLedgerEntry() {
        var command = balancedCommand("ledger-posting-002", new BigDecimal("100.00"), new BigDecimal("90.00"));

        assertThatThrownBy(() -> ledgerService.postLedgerEntry(command))
                .isInstanceOf(UnbalancedLedgerEntryException.class)
                .hasMessageContaining("total debit amount must equal total credit amount");
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
                List.of(new PostLedgerEntryCommand.Line(creditAccountId, new BigDecimal("100.00"))));

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

    private static PostLedgerEntryCommand balancedCommand(
            String postingRequestId, BigDecimal debitAmount, BigDecimal creditAmount) {
        return new PostLedgerEntryCommand(
                postingRequestId,
                "Settlement posting",
                "AED",
                Instant.parse("2026-07-03T09:00:00Z"),
                List.of(new PostLedgerEntryCommand.Line(UUID.randomUUID(), debitAmount)),
                List.of(new PostLedgerEntryCommand.Line(UUID.randomUUID(), creditAmount)));
    }

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

        List<LedgerEntry> entries() {
            return List.copyOf(entries);
        }
    }
}

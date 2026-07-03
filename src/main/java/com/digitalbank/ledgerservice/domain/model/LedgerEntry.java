package com.digitalbank.ledgerservice.domain.model;

import com.digitalbank.ledgerservice.domain.exception.UnbalancedLedgerEntryException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public final class LedgerEntry {

    private final LedgerEntryId id;
    private final String postingRequestId;
    private final String description;
    private final String currency;
    private final Instant effectiveAt;
    private final Instant createdAt;
    private final List<LedgerEntryLine> lines;

    private LedgerEntry(
            LedgerEntryId id,
            String postingRequestId,
            String description,
            String currency,
            Instant effectiveAt,
            Instant createdAt,
            List<LedgerEntryLine> lines) {
        this.id = id;
        this.postingRequestId = postingRequestId;
        this.description = description;
        this.currency = currency;
        this.effectiveAt = effectiveAt;
        this.createdAt = createdAt;
        this.lines = List.copyOf(lines);
    }

    public static LedgerEntry post(
            LedgerEntryId id,
            String postingRequestId,
            String description,
            String currency,
            Instant effectiveAt,
            Instant createdAt,
            List<LedgerEntryLine> lines) {
        var normalizedCurrency = requireCurrency(currency);
        var sortedLines = requireBalancedLines(lines);

        return new LedgerEntry(
                requireId(id),
                requireText(postingRequestId, "postingRequestId"),
                requireText(description, "description"),
                normalizedCurrency,
                requireInstant(effectiveAt, "effectiveAt"),
                requireInstant(createdAt, "createdAt"),
                sortedLines);
    }

    private static LedgerEntryId requireId(LedgerEntryId id) {
        if (id == null) {
            throw new IllegalArgumentException("ledgerEntryId must not be null");
        }
        return id;
    }

    private static Instant requireInstant(Instant value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
        return value;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String requireCurrency(String value) {
        var currency = requireText(value, "currency").toUpperCase();
        if (currency.length() != 3) {
            throw new IllegalArgumentException("currency must have length 3");
        }
        return currency;
    }

    private static List<LedgerEntryLine> requireBalancedLines(List<LedgerEntryLine> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new UnbalancedLedgerEntryException("ledger entry must contain lines");
        }

        var debitLines = lines.stream().filter(line -> line.lineType() == LedgerLineType.DEBIT).toList();
        var creditLines = lines.stream().filter(line -> line.lineType() == LedgerLineType.CREDIT).toList();
        if (debitLines.isEmpty()) {
            throw new UnbalancedLedgerEntryException("ledger entry must contain at least one debit line");
        }
        if (creditLines.isEmpty()) {
            throw new UnbalancedLedgerEntryException("ledger entry must contain at least one credit line");
        }

        var debitTotal = sum(debitLines);
        var creditTotal = sum(creditLines);
        if (debitTotal.compareTo(creditTotal) != 0) {
            throw new UnbalancedLedgerEntryException("total debit amount must equal total credit amount");
        }

        return lines.stream()
                .sorted(Comparator.comparing(LedgerEntryLine::lineType).thenComparing(LedgerEntryLine::accountId))
                .toList();
    }

    private static BigDecimal sum(List<LedgerEntryLine> lines) {
        return lines.stream().map(LedgerEntryLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public LedgerEntryId id() {
        return id;
    }

    public String postingRequestId() {
        return postingRequestId;
    }

    public String description() {
        return description;
    }

    public String currency() {
        return currency;
    }

    public Instant effectiveAt() {
        return effectiveAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public List<LedgerEntryLine> lines() {
        return lines;
    }

    public BigDecimal totalDebitAmount() {
        return sum(lines.stream().filter(line -> line.lineType() == LedgerLineType.DEBIT).toList());
    }

    public BigDecimal totalCreditAmount() {
        return sum(lines.stream().filter(line -> line.lineType() == LedgerLineType.CREDIT).toList());
    }
}

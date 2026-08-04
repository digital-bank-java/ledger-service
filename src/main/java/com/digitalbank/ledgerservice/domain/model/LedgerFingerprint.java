package com.digitalbank.ledgerservice.domain.model;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/** Builds a stable digest for idempotency comparisons at the application boundary. */
public final class LedgerFingerprint {

    private LedgerFingerprint() {}

    public static String forPosting(
            String description,
            String currency,
            Instant effectiveAt,
            List<LedgerEntryLine> lines) {
        return digest(canonical(description, currency, effectiveAt, lines));
    }

    public static String forReversal(
            LedgerEntry source, String description, Instant effectiveAt) {
        var lines = source.lines().stream()
                .map(line -> new LedgerEntryLine(
                        line.accountId(),
                        line.lineType() == LedgerLineType.DEBIT ? LedgerLineType.CREDIT : LedgerLineType.DEBIT,
                        line.amount()))
                .toList();
        return digest(canonical(
                description + "|reversal-of=" + source.id().value(), source.currency(), effectiveAt, lines));
    }

    public static String forStoredEntry(LedgerEntry entry) {
        var description = entry.description();
        if (entry.reversalOfLedgerEntryId() != null) {
            description += "|reversal-of=" + entry.reversalOfLedgerEntryId().value();
        }
        return digest(canonical(description, entry.currency(), entry.effectiveAt(), entry.lines()));
    }

    private static String canonical(
            String description, String currency, Instant effectiveAt, List<LedgerEntryLine> lines) {
        var orderedLines = lines.stream()
                .sorted(Comparator.comparing(LedgerEntryLine::lineType)
                        .thenComparing(LedgerEntryLine::accountId)
                        .thenComparing(LedgerEntryLine::amount))
                .map(line -> String.join(
                        ":",
                        line.lineType().name(),
                        line.accountId().toString(),
                        normalizeAmount(line.amount())))
                .toList();
        return String.join(
                "|",
                description.trim(),
                currency.trim().toUpperCase(),
                effectiveAt.toString(),
                String.join(",", orderedLines));
    }

    private static String normalizeAmount(BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString();
    }

    private static String digest(String value) {
        try {
            var bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            var result = new StringBuilder(bytes.length * 2);
            for (var byteValue : bytes) {
                result.append(String.format("%02x", byteValue));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the JDK", exception);
        }
    }
}

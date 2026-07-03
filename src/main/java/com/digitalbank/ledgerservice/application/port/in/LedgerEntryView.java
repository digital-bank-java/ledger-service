package com.digitalbank.ledgerservice.application.port.in;

import com.digitalbank.ledgerservice.domain.model.LedgerEntry;
import com.digitalbank.ledgerservice.domain.model.LedgerEntryLine;
import com.digitalbank.ledgerservice.domain.model.LedgerLineType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LedgerEntryView(
        String ledgerEntryId,
        String postingRequestId,
        String description,
        String currency,
        Instant effectiveAt,
        Instant createdAt,
        BigDecimal totalDebitAmount,
        BigDecimal totalCreditAmount,
        List<LineView> lines) {

    public static LedgerEntryView fromLedgerEntry(LedgerEntry ledgerEntry) {
        return new LedgerEntryView(
                ledgerEntry.id().value().toString(),
                ledgerEntry.postingRequestId(),
                ledgerEntry.description(),
                ledgerEntry.currency(),
                ledgerEntry.effectiveAt(),
                ledgerEntry.createdAt(),
                ledgerEntry.totalDebitAmount(),
                ledgerEntry.totalCreditAmount(),
                ledgerEntry.lines().stream().map(LineView::fromLedgerEntryLine).toList());
    }

    public record LineView(UUID accountId, LedgerLineType lineType, BigDecimal amount) {

        static LineView fromLedgerEntryLine(LedgerEntryLine line) {
            return new LineView(line.accountId(), line.lineType(), line.amount());
        }
    }
}

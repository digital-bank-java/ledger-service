package com.digitalbank.ledgerservice.application.port.in;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PostLedgerEntryCommand(
        String postingRequestId,
        String description,
        String currency,
        Instant effectiveAt,
        List<Line> debitLines,
        List<Line> creditLines,
        String correlationId,
        String causationId,
        String transactionId,
        String reservationRequestId) {

    public PostLedgerEntryCommand(
            String postingRequestId,
            String description,
            String currency,
            Instant effectiveAt,
            List<Line> debitLines,
            List<Line> creditLines,
            String correlationId,
            String causationId) {
        this(
                postingRequestId,
                description,
                currency,
                effectiveAt,
                debitLines,
                creditLines,
                correlationId,
                causationId,
                null,
                null);
    }

    public record Line(UUID accountId, BigDecimal amount) {}
}

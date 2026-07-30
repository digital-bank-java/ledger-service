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
        List<Line> creditLines) {

    public record Line(UUID accountId, BigDecimal amount) {}
}

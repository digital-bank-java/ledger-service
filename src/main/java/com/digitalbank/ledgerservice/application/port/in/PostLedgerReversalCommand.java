package com.digitalbank.ledgerservice.application.port.in;

import java.time.Instant;
import java.util.UUID;

public record PostLedgerReversalCommand(
        UUID sourceLedgerEntryId,
        String postingRequestId,
        String description,
        Instant effectiveAt,
        String correlationId,
        String causationId) {}

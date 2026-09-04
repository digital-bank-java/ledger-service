package com.digitalbank.ledgerservice.application.port.out;

import java.time.Instant;
import java.util.UUID;

public record LedgerPostingCommandInboxEntry(
        UUID eventId,
        String eventType,
        String topic,
        String aggregateId,
        String postingRequestId,
        String correlationId,
        String causationId,
        String producer,
        String schemaVersion,
        String transactionId,
        String reservationRequestId,
        Instant occurredAt,
        Instant consumedAt) {}

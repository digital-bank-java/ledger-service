package com.digitalbank.ledgerservice.application.port.out;

import java.time.Instant;
import java.util.UUID;

public record ClaimedOutboxEvent(
        UUID eventId,
        UUID leaseId,
        int attempt,
        String eventType,
        String aggregateId,
        String postingRequestId,
        String correlationId,
        String causationId,
        String transactionId,
        String reservationRequestId,
        String payload,
        Instant occurredAt) {}

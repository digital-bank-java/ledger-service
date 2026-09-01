package com.digitalbank.ledgerservice.domain.model;

import java.time.Instant;
import java.util.UUID;

public record LedgerPostingFailureDecision(
        UUID decisionId,
        UUID eventId,
        String postingRequestId,
        String failureCode,
        String failureReason,
        String correlationId,
        String causationId,
        String transactionId,
        String reservationRequestId,
        Instant occurredAt) {}

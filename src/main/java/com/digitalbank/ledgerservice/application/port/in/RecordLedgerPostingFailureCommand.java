package com.digitalbank.ledgerservice.application.port.in;

public record RecordLedgerPostingFailureCommand(
        String postingRequestId,
        String failureCode,
        String failureReason,
        String correlationId,
        String causationId,
        String transactionId,
        String reservationRequestId) {}

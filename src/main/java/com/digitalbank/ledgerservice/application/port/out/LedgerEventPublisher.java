package com.digitalbank.ledgerservice.application.port.out;

import com.digitalbank.ledgerservice.domain.model.LedgerEntry;
import com.digitalbank.ledgerservice.domain.model.LedgerPostingFailureDecision;
import java.time.Instant;
import java.util.UUID;

public interface LedgerEventPublisher {

    void recordPostingCompleted(
            LedgerEntry entry,
            UUID reversalOfLedgerEntryId,
            String correlationId,
            String causationId,
            String transactionId,
            String reservationRequestId,
            Instant occurredAt);

    void recordPostingFailed(LedgerPostingFailureDecision decision);
}

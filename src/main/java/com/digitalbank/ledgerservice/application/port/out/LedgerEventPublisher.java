package com.digitalbank.ledgerservice.application.port.out;

import com.digitalbank.ledgerservice.domain.model.LedgerEntry;
import java.time.Instant;
import java.util.UUID;

public interface LedgerEventPublisher {

    void recordPostingCompleted(
            LedgerEntry entry,
            UUID reversalOfLedgerEntryId,
            String correlationId,
            String causationId,
            Instant occurredAt);

    void recordPostingFailed(
            String postingRequestId,
            String failureCode,
            String failureReason,
            String correlationId,
            String causationId,
            Instant occurredAt);
}

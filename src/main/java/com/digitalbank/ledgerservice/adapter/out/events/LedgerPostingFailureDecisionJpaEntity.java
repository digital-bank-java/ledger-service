package com.digitalbank.ledgerservice.adapter.out.events;

import com.digitalbank.ledgerservice.domain.model.LedgerPostingFailureDecision;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_posting_failure_decisions")
class LedgerPostingFailureDecisionJpaEntity {

    @Id
    @Column(name = "decision_id", nullable = false)
    private UUID decisionId;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "posting_request_id", nullable = false, unique = true, length = 100)
    private String postingRequestId;

    @Column(name = "failure_code", nullable = false, length = 50)
    private String failureCode;

    @Column(name = "failure_reason", nullable = false, length = 500)
    private String failureReason;

    @Column(name = "correlation_id", nullable = false, length = 255)
    private String correlationId;

    @Column(name = "causation_id", nullable = false, length = 255)
    private String causationId;

    @Column(name = "transaction_id", length = 255)
    private String transactionId;

    @Column(name = "reservation_request_id", length = 255)
    private String reservationRequestId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected LedgerPostingFailureDecisionJpaEntity() {}

    LedgerPostingFailureDecisionJpaEntity(
            UUID decisionId,
            UUID eventId,
            String postingRequestId,
            String failureCode,
            String failureReason,
            String correlationId,
            String causationId,
            String transactionId,
            String reservationRequestId,
            Instant occurredAt) {
        this.decisionId = decisionId;
        this.eventId = eventId;
        this.postingRequestId = postingRequestId;
        this.failureCode = failureCode;
        this.failureReason = failureReason;
        this.correlationId = correlationId;
        this.causationId = causationId;
        this.transactionId = transactionId;
        this.reservationRequestId = reservationRequestId;
        this.occurredAt = occurredAt;
    }

    LedgerPostingFailureDecision toDomain() {
        return new LedgerPostingFailureDecision(
                decisionId,
                eventId,
                postingRequestId,
                failureCode,
                failureReason,
                correlationId,
                causationId,
                transactionId,
                reservationRequestId,
                occurredAt);
    }
}

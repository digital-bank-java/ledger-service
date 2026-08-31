package com.digitalbank.ledgerservice.adapter.out.events;

import com.digitalbank.ledgerservice.application.port.out.LedgerPostingFailureDecisionRepository;
import com.digitalbank.ledgerservice.domain.model.LedgerPostingFailureDecision;
import org.springframework.stereotype.Repository;

@Repository
class PostgresLedgerPostingFailureDecisionRepository implements LedgerPostingFailureDecisionRepository {

    private final SpringDataLedgerPostingFailureDecisionRepository repository;

    PostgresLedgerPostingFailureDecisionRepository(SpringDataLedgerPostingFailureDecisionRepository repository) {
        this.repository = repository;
    }

    @Override
    public LedgerPostingFailureDecision save(LedgerPostingFailureDecision decision) {
        repository.saveAndFlush(new LedgerPostingFailureDecisionJpaEntity(
                decision.decisionId(),
                decision.eventId(),
                decision.postingRequestId(),
                decision.failureCode(),
                decision.failureReason(),
                decision.correlationId(),
                decision.causationId(),
                decision.transactionId(),
                decision.reservationRequestId(),
                decision.occurredAt()));
        return decision;
    }
}

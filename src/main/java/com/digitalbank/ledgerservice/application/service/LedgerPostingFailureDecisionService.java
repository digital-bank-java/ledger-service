package com.digitalbank.ledgerservice.application.service;

import com.digitalbank.ledgerservice.application.port.in.RecordLedgerPostingFailureCommand;
import com.digitalbank.ledgerservice.application.port.in.RecordLedgerPostingFailureInputPort;
import com.digitalbank.ledgerservice.application.port.out.LedgerEventPublisher;
import com.digitalbank.ledgerservice.application.port.out.LedgerPostingFailureDecisionRepository;
import com.digitalbank.ledgerservice.domain.model.LedgerPostingFailureDecision;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerPostingFailureDecisionService implements RecordLedgerPostingFailureInputPort {

    private final LedgerPostingFailureDecisionRepository failureDecisionRepository;
    private final LedgerEventPublisher ledgerEventPublisher;
    private final Clock clock;

    public LedgerPostingFailureDecisionService(
            LedgerPostingFailureDecisionRepository failureDecisionRepository,
            LedgerEventPublisher ledgerEventPublisher,
            Clock clock) {
        this.failureDecisionRepository = failureDecisionRepository;
        this.ledgerEventPublisher = ledgerEventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public LedgerPostingFailureDecision recordFailure(RecordLedgerPostingFailureCommand command) {
        var decision = new LedgerPostingFailureDecision(
                UUID.randomUUID(),
                UUID.randomUUID(),
                command.postingRequestId(),
                command.failureCode(),
                command.failureReason(),
                command.correlationId(),
                command.causationId(),
                command.transactionId(),
                command.reservationRequestId(),
                clock.instant());
        var savedDecision = failureDecisionRepository.save(decision);
        ledgerEventPublisher.recordPostingFailed(savedDecision);
        return savedDecision;
    }
}

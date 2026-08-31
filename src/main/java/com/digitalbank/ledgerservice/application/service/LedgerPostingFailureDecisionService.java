package com.digitalbank.ledgerservice.application.service;

import com.digitalbank.ledgerservice.application.port.in.RecordLedgerPostingFailureCommand;
import com.digitalbank.ledgerservice.application.port.in.RecordLedgerPostingFailureInputPort;
import com.digitalbank.ledgerservice.application.port.out.LedgerEventPublisher;
import com.digitalbank.ledgerservice.application.port.out.LedgerPostingFailureDecisionRepository;
import com.digitalbank.ledgerservice.domain.exception.DuplicatePostingRequestException;
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
        var postingRequestId = requireText(command.postingRequestId(), "postingRequestId");
        var failureCode = requireText(command.failureCode(), "failureCode");
        var failureReason = requireText(command.failureReason(), "failureReason");
        var correlationId = requireText(command.correlationId(), "correlationId");
        var causationId = requireText(command.causationId(), "causationId");
        var transactionId = requireText(command.transactionId(), "transactionId");
        var reservationRequestId = requireText(command.reservationRequestId(), "reservationRequestId");

        var existing = failureDecisionRepository.findByPostingRequestId(postingRequestId);
        if (existing.isPresent()) {
            return replayOrConflict(
                    existing.get(),
                    postingRequestId,
                    failureCode,
                    failureReason,
                    correlationId,
                    causationId,
                    transactionId,
                    reservationRequestId);
        }

        var decision = new LedgerPostingFailureDecision(
                UUID.randomUUID(),
                UUID.randomUUID(),
                postingRequestId,
                failureCode,
                failureReason,
                correlationId,
                causationId,
                transactionId,
                reservationRequestId,
                clock.instant());
        var savedDecision = failureDecisionRepository.save(decision);
        ledgerEventPublisher.recordPostingFailed(savedDecision);
        return savedDecision;
    }

    private static LedgerPostingFailureDecision replayOrConflict(
            LedgerPostingFailureDecision existing,
            String postingRequestId,
            String failureCode,
            String failureReason,
            String correlationId,
            String causationId,
            String transactionId,
            String reservationRequestId) {
        if (!matches(existing, failureCode, failureReason, correlationId, causationId, transactionId, reservationRequestId)) {
            throw new DuplicatePostingRequestException(postingRequestId);
        }
        return existing;
    }

    private static boolean matches(
            LedgerPostingFailureDecision existing,
            String failureCode,
            String failureReason,
            String correlationId,
            String causationId,
            String transactionId,
            String reservationRequestId) {
        return normalized(existing.failureCode()).equals(failureCode)
                && normalized(existing.failureReason()).equals(failureReason)
                && normalized(existing.correlationId()).equals(correlationId)
                && normalized(existing.causationId()).equals(causationId)
                && normalized(existing.transactionId()).equals(transactionId)
                && normalized(existing.reservationRequestId()).equals(reservationRequestId);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim();
    }
}

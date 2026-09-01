package com.digitalbank.ledgerservice.application.service;

import com.digitalbank.ledgerservice.application.port.in.GetLedgerEntryInputPort;
import com.digitalbank.ledgerservice.application.port.in.LedgerEntryView;
import com.digitalbank.ledgerservice.application.port.in.PostLedgerEntryCommand;
import com.digitalbank.ledgerservice.application.port.in.PostLedgerEntryInputPort;
import com.digitalbank.ledgerservice.application.port.in.PostLedgerReversalCommand;
import com.digitalbank.ledgerservice.application.port.in.PostLedgerReversalInputPort;
import com.digitalbank.ledgerservice.application.port.in.PostingResult;
import com.digitalbank.ledgerservice.application.port.out.LedgerEntryRepository;
import com.digitalbank.ledgerservice.application.port.out.LedgerEventPublisher;
import com.digitalbank.ledgerservice.domain.exception.DuplicatePostingRequestException;
import com.digitalbank.ledgerservice.domain.exception.LedgerEntryNotFoundException;
import com.digitalbank.ledgerservice.domain.model.LedgerEntry;
import com.digitalbank.ledgerservice.domain.model.LedgerEntryId;
import com.digitalbank.ledgerservice.domain.model.LedgerEntryLine;
import com.digitalbank.ledgerservice.domain.model.LedgerFingerprint;
import com.digitalbank.ledgerservice.domain.model.LedgerLineType;
import java.time.Clock;
import java.util.ArrayList;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

@Service
public class LedgerService implements PostLedgerEntryInputPort, GetLedgerEntryInputPort, PostLedgerReversalInputPort {

    private final LedgerEntryRepository ledgerEntryRepository;
    private final LedgerEventPublisher ledgerEventPublisher;
    private final Clock clock;

    public LedgerService(
            LedgerEntryRepository ledgerEntryRepository, LedgerEventPublisher ledgerEventPublisher, Clock clock) {
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.ledgerEventPublisher = ledgerEventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public PostingResult postLedgerEntry(PostLedgerEntryCommand command) {
        var lines = new ArrayList<LedgerEntryLine>();
        command.debitLines()
                .forEach(line -> lines.add(new LedgerEntryLine(line.accountId(), LedgerLineType.DEBIT, line.amount())));
        command.creditLines()
                .forEach(line -> lines.add(new LedgerEntryLine(line.accountId(), LedgerLineType.CREDIT, line.amount())));

        var fingerprint = LedgerFingerprint.forPosting(
                command.description(), command.currency(), command.effectiveAt(), lines);
        ledgerEntryRepository.lockPostingRequestId(command.postingRequestId().trim());
        var existing = ledgerEntryRepository.findByPostingRequestId(command.postingRequestId().trim());
        if (existing.isPresent()) {
            return replayOrConflict(existing.get(), fingerprint, command.postingRequestId());
        }

        var ledgerEntry = LedgerEntry.post(
                LedgerEntryId.newId(),
                command.postingRequestId(),
                command.description(),
                command.currency(),
                command.effectiveAt(),
                clock.instant(),
                lines,
                fingerprint,
                null);

        var savedEntry = ledgerEntryRepository.save(ledgerEntry);
        ledgerEventPublisher.recordPostingCompleted(
                savedEntry, null, command.correlationId(), command.causationId(), savedEntry.createdAt());
        return new PostingResult(LedgerEntryView.fromLedgerEntry(savedEntry), false);
    }

    @Override
    @Transactional
    public PostingResult reverseLedgerEntry(PostLedgerReversalCommand command) {
        var sourceId = new LedgerEntryId(command.sourceLedgerEntryId());
        var source = ledgerEntryRepository
                .findById(sourceId)
                .orElseThrow(() -> new LedgerEntryNotFoundException(sourceId));
        var fingerprint = LedgerFingerprint.forReversal(source, command.description(), command.effectiveAt());

        ledgerEntryRepository.lockPostingRequestId(command.postingRequestId().trim());
        var existingByRequest = ledgerEntryRepository.findByPostingRequestId(command.postingRequestId().trim());
        if (existingByRequest.isPresent()) {
            return replayOrConflict(existingByRequest.get(), fingerprint, command.postingRequestId());
        }
        if (ledgerEntryRepository.findByReversalOfLedgerEntryId(sourceId).isPresent()) {
            throw new DuplicatePostingRequestException(command.postingRequestId());
        }

        var reversedLines = source.lines().stream()
                .map(line -> new LedgerEntryLine(
                        line.accountId(),
                        line.lineType() == LedgerLineType.DEBIT ? LedgerLineType.CREDIT : LedgerLineType.DEBIT,
                        line.amount()))
                .toList();
        var reversal = LedgerEntry.post(
                LedgerEntryId.newId(),
                command.postingRequestId(),
                command.description(),
                source.currency(),
                command.effectiveAt(),
                clock.instant(),
                reversedLines,
                fingerprint,
                sourceId);
        var savedReversal = ledgerEntryRepository.save(reversal);
        ledgerEventPublisher.recordPostingCompleted(
                savedReversal,
                sourceId.value(),
                command.correlationId(),
                command.causationId(),
                savedReversal.createdAt());
        return new PostingResult(LedgerEntryView.fromLedgerEntry(savedReversal), false);
    }

    private PostingResult replayOrConflict(
            LedgerEntry existing, String expectedFingerprint, String postingRequestId) {
        var actualFingerprint = existing.requestFingerprint() == null
                ? LedgerFingerprint.forStoredEntry(existing)
                : existing.requestFingerprint();
        if (!actualFingerprint.equals(expectedFingerprint)) {
            throw new DuplicatePostingRequestException(postingRequestId);
        }
        return new PostingResult(LedgerEntryView.fromLedgerEntry(existing), true);
    }

    @Override
    public LedgerEntryView getLedgerEntry(LedgerEntryId ledgerEntryId) {
        return ledgerEntryRepository
                .findById(ledgerEntryId)
                .map(LedgerEntryView::fromLedgerEntry)
                .orElseThrow(() -> new LedgerEntryNotFoundException(ledgerEntryId));
    }
}

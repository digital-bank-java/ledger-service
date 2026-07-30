package com.digitalbank.ledgerservice.application.service;

import com.digitalbank.ledgerservice.application.port.in.GetLedgerEntryInputPort;
import com.digitalbank.ledgerservice.application.port.in.LedgerEntryView;
import com.digitalbank.ledgerservice.application.port.in.PostLedgerEntryCommand;
import com.digitalbank.ledgerservice.application.port.in.PostLedgerEntryInputPort;
import com.digitalbank.ledgerservice.application.port.out.LedgerEntryRepository;
import com.digitalbank.ledgerservice.domain.exception.DuplicatePostingRequestException;
import com.digitalbank.ledgerservice.domain.exception.LedgerEntryNotFoundException;
import com.digitalbank.ledgerservice.domain.model.LedgerEntry;
import com.digitalbank.ledgerservice.domain.model.LedgerEntryId;
import com.digitalbank.ledgerservice.domain.model.LedgerEntryLine;
import com.digitalbank.ledgerservice.domain.model.LedgerLineType;
import java.time.Clock;
import java.util.ArrayList;
import org.springframework.stereotype.Service;

@Service
public class LedgerService implements PostLedgerEntryInputPort, GetLedgerEntryInputPort {

    private final LedgerEntryRepository ledgerEntryRepository;
    private final Clock clock;

    public LedgerService(LedgerEntryRepository ledgerEntryRepository, Clock clock) {
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.clock = clock;
    }

    @Override
    public LedgerEntryView postLedgerEntry(PostLedgerEntryCommand command) {
        if (ledgerEntryRepository.existsByPostingRequestId(command.postingRequestId())) {
            throw new DuplicatePostingRequestException(command.postingRequestId());
        }

        var lines = new ArrayList<LedgerEntryLine>();
        command.debitLines()
                .forEach(line -> lines.add(new LedgerEntryLine(line.accountId(), LedgerLineType.DEBIT, line.amount())));
        command.creditLines()
                .forEach(line -> lines.add(new LedgerEntryLine(line.accountId(), LedgerLineType.CREDIT, line.amount())));

        var ledgerEntry = LedgerEntry.post(
                LedgerEntryId.newId(),
                command.postingRequestId(),
                command.description(),
                command.currency(),
                command.effectiveAt(),
                clock.instant(),
                lines);

        return LedgerEntryView.fromLedgerEntry(ledgerEntryRepository.save(ledgerEntry));
    }

    @Override
    public LedgerEntryView getLedgerEntry(LedgerEntryId ledgerEntryId) {
        return ledgerEntryRepository
                .findById(ledgerEntryId)
                .map(LedgerEntryView::fromLedgerEntry)
                .orElseThrow(() -> new LedgerEntryNotFoundException(ledgerEntryId));
    }
}

package com.digitalbank.ledgerservice.application.port.in;

import com.digitalbank.ledgerservice.domain.model.LedgerPostingFailureDecision;

public interface RecordLedgerPostingFailureInputPort {

    LedgerPostingFailureDecision recordFailure(RecordLedgerPostingFailureCommand command);
}

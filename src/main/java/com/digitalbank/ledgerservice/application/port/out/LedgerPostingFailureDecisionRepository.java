package com.digitalbank.ledgerservice.application.port.out;

import com.digitalbank.ledgerservice.domain.model.LedgerPostingFailureDecision;

public interface LedgerPostingFailureDecisionRepository {

    LedgerPostingFailureDecision save(LedgerPostingFailureDecision decision);
}

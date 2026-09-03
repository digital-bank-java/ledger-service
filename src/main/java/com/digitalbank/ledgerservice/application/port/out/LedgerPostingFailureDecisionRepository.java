package com.digitalbank.ledgerservice.application.port.out;

import com.digitalbank.ledgerservice.domain.model.LedgerPostingFailureDecision;
import java.util.Optional;

public interface LedgerPostingFailureDecisionRepository {

    Optional<LedgerPostingFailureDecision> findByPostingRequestId(String postingRequestId);

    LedgerPostingFailureDecision save(LedgerPostingFailureDecision decision);
}

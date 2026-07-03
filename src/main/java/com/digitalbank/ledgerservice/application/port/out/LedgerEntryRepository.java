package com.digitalbank.ledgerservice.application.port.out;

import com.digitalbank.ledgerservice.domain.model.LedgerEntry;
import com.digitalbank.ledgerservice.domain.model.LedgerEntryId;
import java.util.Optional;

public interface LedgerEntryRepository {

    LedgerEntry save(LedgerEntry ledgerEntry);

    Optional<LedgerEntry> findById(LedgerEntryId ledgerEntryId);

    boolean existsByPostingRequestId(String postingRequestId);
}

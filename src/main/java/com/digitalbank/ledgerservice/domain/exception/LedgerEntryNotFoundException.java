package com.digitalbank.ledgerservice.domain.exception;

import com.digitalbank.ledgerservice.domain.model.LedgerEntryId;

public class LedgerEntryNotFoundException extends RuntimeException {

    private final LedgerEntryId ledgerEntryId;

    public LedgerEntryNotFoundException(LedgerEntryId ledgerEntryId) {
        super("Ledger entry not found: " + ledgerEntryId.value());
        this.ledgerEntryId = ledgerEntryId;
    }

    public LedgerEntryId ledgerEntryId() {
        return ledgerEntryId;
    }
}

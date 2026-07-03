package com.digitalbank.ledgerservice.application.port.in;

import com.digitalbank.ledgerservice.domain.model.LedgerEntryId;

public interface GetLedgerEntryInputPort {

    LedgerEntryView getLedgerEntry(LedgerEntryId ledgerEntryId);
}

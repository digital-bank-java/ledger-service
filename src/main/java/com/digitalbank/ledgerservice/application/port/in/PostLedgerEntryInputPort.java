package com.digitalbank.ledgerservice.application.port.in;

public interface PostLedgerEntryInputPort {

    PostingResult postLedgerEntry(PostLedgerEntryCommand command);
}

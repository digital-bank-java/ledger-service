package com.digitalbank.ledgerservice.application.port.in;

public interface PostLedgerEntryInputPort {

    LedgerEntryView postLedgerEntry(PostLedgerEntryCommand command);
}

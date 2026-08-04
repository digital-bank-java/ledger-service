package com.digitalbank.ledgerservice.application.port.in;

public interface PostLedgerReversalInputPort {

    PostingResult reverseLedgerEntry(PostLedgerReversalCommand command);
}

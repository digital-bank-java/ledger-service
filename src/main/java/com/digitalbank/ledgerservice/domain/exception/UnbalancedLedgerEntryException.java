package com.digitalbank.ledgerservice.domain.exception;

public class UnbalancedLedgerEntryException extends RuntimeException {

    public UnbalancedLedgerEntryException(String message) {
        super(message);
    }
}

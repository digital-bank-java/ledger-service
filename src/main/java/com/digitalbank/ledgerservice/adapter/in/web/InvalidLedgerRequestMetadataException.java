package com.digitalbank.ledgerservice.adapter.in.web;

final class InvalidLedgerRequestMetadataException extends RuntimeException {

    InvalidLedgerRequestMetadataException(String headerName) {
        super(headerName + " header must be present and non-blank");
    }
}

package com.digitalbank.ledgerservice.domain.model;

import java.util.UUID;

public record LedgerEntryId(UUID value) {

    public LedgerEntryId {
        if (value == null) {
            throw new IllegalArgumentException("ledgerEntryId must not be null");
        }
    }

    public static LedgerEntryId newId() {
        return new LedgerEntryId(UUID.randomUUID());
    }
}

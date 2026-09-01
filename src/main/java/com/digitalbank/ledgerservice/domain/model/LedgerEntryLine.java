package com.digitalbank.ledgerservice.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

public record LedgerEntryLine(UUID accountId, LedgerLineType lineType, BigDecimal amount) {

    private static final int MAX_FRACTIONAL_DIGITS = 4;

    public LedgerEntryLine {
        if (accountId == null) {
            throw new IllegalArgumentException("accountId must not be null");
        }
        if (lineType == null) {
            throw new IllegalArgumentException("lineType must not be null");
        }
        if (amount == null) {
            throw new IllegalArgumentException("amount must not be null");
        }
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (amount.scale() > MAX_FRACTIONAL_DIGITS) {
            throw new IllegalArgumentException("amount must have no more than 4 decimal places");
        }
    }
}

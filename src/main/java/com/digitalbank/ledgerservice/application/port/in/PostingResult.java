package com.digitalbank.ledgerservice.application.port.in;

public record PostingResult(LedgerEntryView entry, boolean replay) {

    public String ledgerEntryId() { return entry.ledgerEntryId(); }

    public String postingRequestId() { return entry.postingRequestId(); }

    public String currency() { return entry.currency(); }

    public java.time.Instant createdAt() { return entry.createdAt(); }

    public java.math.BigDecimal totalDebitAmount() { return entry.totalDebitAmount(); }

    public java.math.BigDecimal totalCreditAmount() { return entry.totalCreditAmount(); }

    public java.util.List<LedgerEntryView.LineView> lines() { return entry.lines(); }
}

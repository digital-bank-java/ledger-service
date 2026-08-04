package com.digitalbank.ledgerservice.adapter.in.web;

import com.digitalbank.ledgerservice.application.port.in.LedgerEntryView;
import com.digitalbank.ledgerservice.domain.model.LedgerLineType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

record LedgerEntryResponse(
        String ledgerEntryId,
        String postingRequestId,
        String description,
        String currency,
        Instant effectiveAt,
        Instant createdAt,
        String reversalOfLedgerEntryId,
        BigDecimal totalDebitAmount,
        BigDecimal totalCreditAmount,
        List<LineResponse> lines) {

    static LedgerEntryResponse from(LedgerEntryView view) {
        return new LedgerEntryResponse(
                view.ledgerEntryId(),
                view.postingRequestId(),
                view.description(),
                view.currency(),
                view.effectiveAt(),
                view.createdAt(),
                view.reversalOfLedgerEntryId(),
                view.totalDebitAmount(),
                view.totalCreditAmount(),
                view.lines().stream().map(LineResponse::from).toList());
    }

    record LineResponse(UUID accountId, LedgerLineType lineType, BigDecimal amount) {

        static LineResponse from(LedgerEntryView.LineView lineView) {
            return new LineResponse(lineView.accountId(), lineView.lineType(), lineView.amount());
        }
    }
}

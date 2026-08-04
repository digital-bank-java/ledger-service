package com.digitalbank.ledgerservice.adapter.out.persistence;

import com.digitalbank.ledgerservice.domain.model.LedgerEntry;
import com.digitalbank.ledgerservice.domain.model.LedgerEntryId;
import com.digitalbank.ledgerservice.domain.model.LedgerEntryLine;
import java.util.ArrayList;

final class LedgerEntryJpaMapper {

    private LedgerEntryJpaMapper() {}

    static LedgerEntryJpaEntity toEntity(LedgerEntry ledgerEntry) {
        var entity = new LedgerEntryJpaEntity(
                ledgerEntry.id().value(),
                ledgerEntry.postingRequestId(),
                ledgerEntry.description(),
                ledgerEntry.currency(),
                ledgerEntry.effectiveAt(),
                ledgerEntry.createdAt(),
                ledgerEntry.requestFingerprint(),
                ledgerEntry.reversalOfLedgerEntryId() == null ? null : ledgerEntry.reversalOfLedgerEntryId().value());

        var lineNumber = 1;
        for (var line : ledgerEntry.lines()) {
            entity.addLine(new LedgerEntryLineJpaEntity(
                    lineNumber++, line.accountId(), line.lineType(), line.amount()));
        }
        return entity;
    }

    static LedgerEntry toDomain(LedgerEntryJpaEntity entity) {
        var lines = new ArrayList<LedgerEntryLine>();
        for (var line : entity.lines()) {
            lines.add(new LedgerEntryLine(line.accountId(), line.lineType(), line.amount()));
        }

        return LedgerEntry.post(
                new LedgerEntryId(entity.id()),
                entity.postingRequestId(),
                entity.description(),
                entity.currency(),
                entity.effectiveAt(),
                entity.createdAt(),
                lines,
                entity.requestFingerprint(),
                entity.reversalOfEntryId() == null ? null : new LedgerEntryId(entity.reversalOfEntryId()));
    }
}

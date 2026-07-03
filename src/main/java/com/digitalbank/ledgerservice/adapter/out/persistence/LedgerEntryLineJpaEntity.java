package com.digitalbank.ledgerservice.adapter.out.persistence;

import com.digitalbank.ledgerservice.domain.model.LedgerLineType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "ledger_entry_lines")
class LedgerEntryLineJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entry_id", nullable = false)
    private LedgerEntryJpaEntity entry;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "line_type", nullable = false, length = 10)
    private LedgerLineType lineType;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    protected LedgerEntryLineJpaEntity() {}

    LedgerEntryLineJpaEntity(int lineNumber, UUID accountId, LedgerLineType lineType, BigDecimal amount) {
        this.lineNumber = lineNumber;
        this.accountId = accountId;
        this.lineType = lineType;
        this.amount = amount;
    }

    void attachTo(LedgerEntryJpaEntity entry) {
        this.entry = entry;
    }

    int lineNumber() {
        return lineNumber;
    }

    UUID accountId() {
        return accountId;
    }

    LedgerLineType lineType() {
        return lineType;
    }

    BigDecimal amount() {
        return amount;
    }
}

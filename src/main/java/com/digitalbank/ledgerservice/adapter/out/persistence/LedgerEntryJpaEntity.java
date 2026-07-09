package com.digitalbank.ledgerservice.adapter.out.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries")
class LedgerEntryJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "posting_request_id", nullable = false, unique = true, length = 100)
    private String postingRequestId;

    @Column(name = "description", nullable = false, length = 255)
    private String description;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "effective_at", nullable = false)
    private Instant effectiveAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "entry", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNumber ASC")
    private List<LedgerEntryLineJpaEntity> lines = new ArrayList<>();

    protected LedgerEntryJpaEntity() {}

    LedgerEntryJpaEntity(
            UUID id,
            String postingRequestId,
            String description,
            String currency,
            Instant effectiveAt,
            Instant createdAt) {
        this.id = id;
        this.postingRequestId = postingRequestId;
        this.description = description;
        this.currency = currency;
        this.effectiveAt = effectiveAt;
        this.createdAt = createdAt;
    }

    void addLine(LedgerEntryLineJpaEntity line) {
        lines.add(line);
        line.attachTo(this);
    }

    UUID id() {
        return id;
    }

    String postingRequestId() {
        return postingRequestId;
    }

    String description() {
        return description;
    }

    String currency() {
        return currency;
    }

    Instant effectiveAt() {
        return effectiveAt;
    }

    Instant createdAt() {
        return createdAt;
    }

    List<LedgerEntryLineJpaEntity> lines() {
        return lines;
    }
}

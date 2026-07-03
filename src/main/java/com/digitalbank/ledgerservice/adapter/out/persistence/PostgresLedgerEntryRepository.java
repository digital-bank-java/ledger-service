package com.digitalbank.ledgerservice.adapter.out.persistence;

import com.digitalbank.ledgerservice.application.port.out.LedgerEntryRepository;
import com.digitalbank.ledgerservice.domain.model.LedgerEntry;
import com.digitalbank.ledgerservice.domain.model.LedgerEntryId;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class PostgresLedgerEntryRepository implements LedgerEntryRepository {

    private final SpringDataLedgerEntryRepository repository;

    PostgresLedgerEntryRepository(SpringDataLedgerEntryRepository repository) {
        this.repository = repository;
    }

    @Override
    public LedgerEntry save(LedgerEntry ledgerEntry) {
        return LedgerEntryJpaMapper.toDomain(repository.saveAndFlush(LedgerEntryJpaMapper.toEntity(ledgerEntry)));
    }

    @Override
    public Optional<LedgerEntry> findById(LedgerEntryId ledgerEntryId) {
        return repository.findWithLinesById(ledgerEntryId.value()).map(LedgerEntryJpaMapper::toDomain);
    }

    @Override
    public boolean existsByPostingRequestId(String postingRequestId) {
        return repository.existsByPostingRequestId(postingRequestId);
    }
}

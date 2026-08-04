package com.digitalbank.ledgerservice.adapter.out.persistence;

import com.digitalbank.ledgerservice.application.port.out.LedgerEntryRepository;
import com.digitalbank.ledgerservice.domain.model.LedgerEntry;
import com.digitalbank.ledgerservice.domain.model.LedgerEntryId;
import java.util.Optional;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class PostgresLedgerEntryRepository implements LedgerEntryRepository {

    private final SpringDataLedgerEntryRepository repository;
    private final JdbcTemplate jdbcTemplate;

    PostgresLedgerEntryRepository(SpringDataLedgerEntryRepository repository, JdbcTemplate jdbcTemplate) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
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
    public Optional<LedgerEntry> findByPostingRequestId(String postingRequestId) {
        return repository.findWithLinesByPostingRequestId(postingRequestId).map(LedgerEntryJpaMapper::toDomain);
    }

    @Override
    public Optional<LedgerEntry> findByReversalOfLedgerEntryId(LedgerEntryId ledgerEntryId) {
        return repository
                .findWithLinesByReversalOfEntryId(ledgerEntryId.value())
                .map(LedgerEntryJpaMapper::toDomain);
    }

    @Override
    public void lockPostingRequestId(String postingRequestId) {
        jdbcTemplate.execute(
                "select pg_advisory_xact_lock(hashtextextended(?, 0))",
                (PreparedStatementCallback<Void>) statement -> {
                    statement.setString(1, postingRequestId);
                    statement.execute();
                    return null;
                });
    }
}

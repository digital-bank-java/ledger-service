package com.digitalbank.ledgerservice.adapter.out.persistence;

import com.digitalbank.ledgerservice.application.port.out.LedgerPostingCommandInboxEntry;
import com.digitalbank.ledgerservice.application.port.out.LedgerPostingCommandInboxRepository;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class PostgresLedgerPostingCommandInboxRepository implements LedgerPostingCommandInboxRepository {

    private final SpringDataLedgerPostingCommandInboxRepository repository;

    PostgresLedgerPostingCommandInboxRepository(SpringDataLedgerPostingCommandInboxRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean existsByEventId(UUID eventId) {
        return repository.existsById(eventId);
    }

    @Override
    public void save(LedgerPostingCommandInboxEntry entry) {
        repository.saveAndFlush(new LedgerPostingCommandInboxJpaEntity(
                entry.eventId(),
                entry.eventType(),
                entry.topic(),
                entry.aggregateId(),
                entry.postingRequestId(),
                entry.correlationId(),
                entry.causationId(),
                entry.producer(),
                entry.schemaVersion(),
                entry.transactionId(),
                entry.reservationRequestId(),
                entry.occurredAt(),
                entry.consumedAt()));
    }
}

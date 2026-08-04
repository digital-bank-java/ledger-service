package com.digitalbank.ledgerservice.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataLedgerEntryRepository extends JpaRepository<LedgerEntryJpaEntity, UUID> {

    @EntityGraph(attributePaths = "lines")
    java.util.Optional<LedgerEntryJpaEntity> findWithLinesById(UUID id);

    @EntityGraph(attributePaths = "lines")
    java.util.Optional<LedgerEntryJpaEntity> findWithLinesByPostingRequestId(String postingRequestId);

    @EntityGraph(attributePaths = "lines")
    java.util.Optional<LedgerEntryJpaEntity> findWithLinesByReversalOfEntryId(UUID reversalOfEntryId);
}

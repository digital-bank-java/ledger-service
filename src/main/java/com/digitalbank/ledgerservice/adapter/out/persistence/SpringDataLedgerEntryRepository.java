package com.digitalbank.ledgerservice.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataLedgerEntryRepository extends JpaRepository<LedgerEntryJpaEntity, UUID> {

    @EntityGraph(attributePaths = "lines")
    java.util.Optional<LedgerEntryJpaEntity> findWithLinesById(UUID id);

    boolean existsByPostingRequestId(String postingRequestId);
}

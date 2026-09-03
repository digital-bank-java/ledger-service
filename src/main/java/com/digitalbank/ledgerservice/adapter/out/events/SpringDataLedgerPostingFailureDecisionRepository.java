package com.digitalbank.ledgerservice.adapter.out.events;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataLedgerPostingFailureDecisionRepository
        extends JpaRepository<LedgerPostingFailureDecisionJpaEntity, UUID> {

    java.util.Optional<LedgerPostingFailureDecisionJpaEntity> findByPostingRequestId(String postingRequestId);
}

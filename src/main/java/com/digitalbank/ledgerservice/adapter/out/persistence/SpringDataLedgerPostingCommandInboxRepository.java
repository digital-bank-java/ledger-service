package com.digitalbank.ledgerservice.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataLedgerPostingCommandInboxRepository
        extends JpaRepository<LedgerPostingCommandInboxJpaEntity, UUID> {}

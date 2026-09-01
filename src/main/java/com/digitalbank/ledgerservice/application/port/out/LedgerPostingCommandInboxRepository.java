package com.digitalbank.ledgerservice.application.port.out;

import java.util.UUID;

public interface LedgerPostingCommandInboxRepository {

    boolean existsByEventId(UUID eventId);

    void save(LedgerPostingCommandInboxEntry entry);
}

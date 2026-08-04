package com.digitalbank.ledgerservice.application.port.out;

import com.digitalbank.ledgerservice.domain.model.LedgerEntry;
import com.digitalbank.ledgerservice.domain.model.LedgerEntryId;
import java.util.Optional;

public interface LedgerEntryRepository {

    LedgerEntry save(LedgerEntry ledgerEntry);

    Optional<LedgerEntry> findById(LedgerEntryId ledgerEntryId);

    Optional<LedgerEntry> findByPostingRequestId(String postingRequestId);

    default Optional<LedgerEntry> findByReversalOfLedgerEntryId(LedgerEntryId ledgerEntryId) {
        return Optional.empty();
    }

    /** Compatibility helper for narrow unit-test fakes and legacy adapters. */
    default boolean existsByPostingRequestId(String postingRequestId) {
        return findByPostingRequestId(postingRequestId).isPresent();
    }

    /** Serializes requests with the same id for the duration of the surrounding transaction. */
    default void lockPostingRequestId(String postingRequestId) {}
}

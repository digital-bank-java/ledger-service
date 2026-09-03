package com.digitalbank.ledgerservice.adapter.out.events;

import com.digitalbank.ledgerservice.application.port.out.ClaimedOutboxEvent;
import com.digitalbank.ledgerservice.application.port.out.LedgerOutboxDeliveryRepository;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JdbcLedgerOutboxDeliveryRepository implements LedgerOutboxDeliveryRepository {

    private final JdbcTemplate jdbcTemplate;

    JdbcLedgerOutboxDeliveryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public List<ClaimedOutboxEvent> claimAvailable(Instant now, int batchSize, Duration leaseDuration) {
        var candidates = jdbcTemplate.query(
                """
                select event_id, attempts, event_type, aggregate_id, posting_request_id, correlation_id, causation_id,
                       transaction_id, reservation_request_id, payload::text, created_at
                from ledger_outbox_events
                where ((status = 'PENDING' and next_attempt_at <= ?)
                   or (status = 'DELIVERING' and lease_expires_at <= ?))
                  and nullif(btrim(posting_request_id), '') is not null
                  and nullif(btrim(correlation_id), '') is not null
                  and nullif(btrim(causation_id), '') is not null
                  and nullif(btrim(transaction_id), '') is not null
                  and nullif(btrim(reservation_request_id), '') is not null
                order by created_at, event_id
                for update skip locked
                limit ?
                """,
                (resultSet, rowNumber) -> new Candidate(
                        resultSet.getObject("event_id", UUID.class),
                        resultSet.getInt("attempts"),
                        resultSet.getString("event_type"),
                        resultSet.getString("aggregate_id"),
                        resultSet.getString("posting_request_id"),
                        resultSet.getString("correlation_id"),
                        resultSet.getString("causation_id"),
                        resultSet.getString("transaction_id"),
                        resultSet.getString("reservation_request_id"),
                        resultSet.getString("payload"),
                        resultSet.getTimestamp("created_at").toInstant()),
                Timestamp.from(now),
                Timestamp.from(now),
                batchSize);

        return candidates.stream()
                .map(candidate -> claim(candidate, now, leaseDuration))
                .toList();
    }

    private ClaimedOutboxEvent claim(Candidate candidate, Instant now, Duration leaseDuration) {
        var leaseId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                update ledger_outbox_events
                set status = 'DELIVERING', attempts = attempts + 1, lease_id = ?, lease_expires_at = ?,
                    quarantined_at = null
                where event_id = ?
                """,
                leaseId,
                Timestamp.from(now.plus(leaseDuration)),
                candidate.eventId());
        return new ClaimedOutboxEvent(
                candidate.eventId(),
                leaseId,
                candidate.attempts() + 1,
                candidate.eventType(),
                candidate.aggregateId(),
                candidate.postingRequestId(),
                candidate.correlationId(),
                candidate.causationId(),
                candidate.transactionId(),
                candidate.reservationRequestId(),
                candidate.payload(),
                candidate.occurredAt());
    }

    @Override
    public void markPublished(ClaimedOutboxEvent event, Instant publishedAt) {
        updateForLease(
                """
                update ledger_outbox_events
                set status = 'PUBLISHED', published_at = ?, lease_id = null, lease_expires_at = null
                where event_id = ? and lease_id = ? and status = 'DELIVERING'
                """,
                publishedAt,
                event,
                null);
    }

    @Override
    public void markRetry(ClaimedOutboxEvent event, Instant nextAttemptAt, String error) {
        updateForLease(
                """
                update ledger_outbox_events
                set status = 'PENDING', next_attempt_at = ?, last_error = ?, lease_id = null, lease_expires_at = null
                where event_id = ? and lease_id = ? and status = 'DELIVERING'
                """,
                nextAttemptAt,
                event,
                error);
    }

    @Override
    public void markQuarantined(ClaimedOutboxEvent event, Instant quarantinedAt, String error) {
        updateForLease(
                """
                update ledger_outbox_events
                set status = 'QUARANTINED', quarantined_at = ?, last_error = ?, lease_id = null, lease_expires_at = null
                where event_id = ? and lease_id = ? and status = 'DELIVERING'
                """,
                quarantinedAt,
                event,
                error);
    }

    private void updateForLease(
            String sql, Instant timestamp, ClaimedOutboxEvent event, String error) {
        int updated;
        if (error == null) {
            updated = jdbcTemplate.update(sql, Timestamp.from(timestamp), event.eventId(), event.leaseId());
        } else {
            updated = jdbcTemplate.update(sql, Timestamp.from(timestamp), error, event.eventId(), event.leaseId());
        }
        if (updated != 1) {
            throw new IllegalStateException("Outbox delivery lease is no longer owned for event " + event.eventId());
        }
    }

    private record Candidate(
            UUID eventId,
            int attempts,
            String eventType,
            String aggregateId,
            String postingRequestId,
            String correlationId,
            String causationId,
            String transactionId,
            String reservationRequestId,
            String payload,
            Instant occurredAt) {}
}

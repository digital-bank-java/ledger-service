# Ledger Outbox Delivery Operations

## State Machine

Every outbox record starts as `PENDING`. A worker claims it under a PostgreSQL
row lock and changes it to `DELIVERING` with a lease ID and expiry. The worker
uses the immutable `eventId`, payload, and `aggregateId` for every attempt.

| State | Meaning | Next state |
| --- | --- | --- |
| `PENDING` | Durable event waiting for its next eligible attempt. | `DELIVERING` |
| `DELIVERING` | A worker owns a bounded lease. An expired lease is eligible for a new claim. | `PUBLISHED`, `PENDING`, or `QUARANTINED` |
| `PUBLISHED` | Kafka accepted the record and the lease owner recorded completion. | Terminal |
| `QUARANTINED` | The bounded attempt limit was exhausted. The event and error remain durable. | Explicit authorized recovery only |

Delivery is at-least-once. A process failure after Kafka accepts a record but
before PostgreSQL records `PUBLISHED` can resend the same `eventId`. Consumers
must use their inbox keyed by `eventId`.

## Runtime Configuration

Delivery is disabled by default. Enable it only when the environment supplies
the usual Spring Kafka bootstrap and security configuration:

```properties
ledger.outbox.delivery.enabled=true
ledger.outbox.delivery.batch-size=25
ledger.outbox.delivery.max-attempts=5
ledger.outbox.delivery.lease-duration=PT1M
ledger.outbox.delivery.retry-delay=PT5S
ledger.outbox.delivery.poll-delay=PT5S
ledger.outbox.delivery.publish-timeout=PT10S
```

The Kafka key is `aggregateId`. Ordering is guaranteed only for equal keys on
the same topic. It is not guaranteed across `ledger.posting.completed.v1` and
`ledger.posting.failed.v1`.
The Kafka producer acknowledgement wait is bounded by `publish-timeout`; timeout
or send failure is surfaced to the delivery worker so it can retry or quarantine
the immutable outbox row.

## Monitoring And Recovery

Alert on non-zero `QUARANTINED` records and prolonged `DELIVERING` leases. The
following query exposes exhausted delivery without exposing payload data:

```sql
select event_id, event_type, aggregate_id, attempts, last_error, quarantined_at
from ledger_outbox_events
where status = 'QUARANTINED'
order by quarantined_at;
```

Investigate the broker and credentials before replay. An authorized operator
may requeue a quarantined record by clearing only delivery-state fields. Never
change `event_id`, payload, metadata, or aggregate ID:

```sql
update ledger_outbox_events
set status = 'PENDING',
    next_attempt_at = now(),
    lease_id = null,
    lease_expires_at = null
where event_id = '<event UUID>'
  and status = 'QUARANTINED';
```

The original event identity is retained. Recovery does not create a new ledger
decision or a new financial event.

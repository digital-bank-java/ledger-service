# Ledger Posting Requested Command

This Sprint 3 contract is local/SIT scoped. It governs the inbound Kafka
command consumed by Ledger Service before broader environment rollout.

## Transport

- Topic: `ledger.posting.requested.v1`
- Event type: `LedgerPostingRequested.v1`
- Schema version: `1.0.0`
- Producer: `transaction-service`
- Delivery semantics: at-least-once

The listener is disabled by default. Enable it explicitly with:

```properties
ledger.posting.consumer.enabled=true
ledger.posting.consumer.topic=ledger.posting.requested.v1
ledger.posting.consumer.group-id=ledger-service
```

## Required Headers

- `event-id`
- `correlation-id`
- `causation-id`
- `producer`
- `schema-version`
- `occurred-at`

Headers must be present, non-blank, and match the corresponding payload
fields exactly.

## Required Payload

```json
{
  "eventId": "uuid",
  "eventType": "LedgerPostingRequested.v1",
  "schemaVersion": "1.0.0",
  "producer": "transaction-service",
  "occurredAt": "2026-09-01T07:55:00Z",
  "aggregateId": "transfer-kafka-001",
  "correlationId": "correlation-kafka-001",
  "causationId": "causation-kafka-001",
  "transactionId": "transfer-kafka-001",
  "reservationRequestId": "reservation-posting-kafka-001",
  "postingRequestId": "posting-kafka-001",
  "description": "Kafka posting",
  "currency": "AED",
  "effectiveAt": "2026-09-01T07:50:00Z",
  "debitLines": [
    { "accountId": "uuid", "amount": 25.00 }
  ],
  "creditLines": [
    { "accountId": "uuid", "amount": 25.00 }
  ]
}
```

The payload is validated with the same core rules as the internal HTTP posting
request:

- `currency` must be three uppercase letters
- `debitLines` and `creditLines` must both be non-empty
- line amounts must be positive and use at most four decimal places

## Outcome Rules

- A valid command maps directly to `PostLedgerEntryCommand`
- Successful posting emits the existing `LedgerPostingCompleted.v1` outbox flow
- Terminal validation or conflict failures emit the existing
  `LedgerPostingFailed.v1` outbox flow when governed identifiers are available
- Redelivery of the same `eventId` is absorbed by the durable inbox table
  `ledger_posting_command_inbox`
- Append-only and posting-request uniqueness safeguards remain the source of
  truth for immutable ledger outcomes

# Ledger Outbox Delivery Design

## Purpose

Complete the ledger outbox introduced by foundation PR #14 with governed event
metadata, durable failure decisions, and an at-least-once delivery lifecycle.
This change implements [parent task #170](https://github.com/digital-bank-java/.github/issues/170)
on top of [foundation PR #14](https://github.com/digital-bank-java/ledger-service/pull/14)
and follows the [governed event contract](https://github.com/digital-bank-java/.github/pull/137).

## Compatibility

`transactionId` and `reservationRequestId` are additive, nullable request
fields. A caller that provides them has both values carried through the posting
command, outbox row, and payload. Existing direct ledger callers that do not
have a business transaction continue to post successfully; the outbox record
and payload omit these values. The service never derives either identifier from
the correlation ID, posting request ID, or a generated value.

The governed AsyncAPI schema requires both identifiers for saga-managed events.
Consequently, legacy direct-post events remain backward-compatible ledger facts
but are not eligible for saga settlement/release until their producer supplies
the governed identifiers. This limitation is explicit rather than fabricating a
transaction identifier.

## Ledger Decisions

A successful immutable `ledger_entries` row is the durable completed decision
and is inserted atomically with its `LedgerPostingCompleted.v1` outbox row.
The service also provides an internal application input port for a caller that
has reached a terminal ledger business failure. It writes an append-only
`ledger_posting_failure_decisions` row and exactly one
`LedgerPostingFailed.v1` outbox row in the same transaction.

HTTP validation, authorization, malformed or unbalanced requests, missing
reversal sources, and failures before either durable decision is committed do
not invoke the failure-decision port and therefore do not emit a financial
failure event. The existing HTTP endpoint behavior remains unchanged.

## Delivery Model

Outbox rows are immutable in identity and payload. Their delivery fields use
the following state machine:

`PENDING -> DELIVERING -> PUBLISHED`

`PENDING -> DELIVERING -> PENDING` on a retryable transport failure below the
attempt limit.

`PENDING -> DELIVERING -> QUARANTINED` after the configured attempt limit.

`DELIVERING -> DELIVERING` with a new lease when a prior delivery lease expires
after a worker crash.

Workers claim rows with a database lock and a unique lease token, publish
outside the database transaction, then complete the transition only if they
still own that lease. Attempts are incremented on every claim. Delivery is
at-least-once: a crash after Kafka accepts the record but before `PUBLISHED` is
committed can resend the same event. Every retry uses the persisted `eventId`
and immutable payload, so consumers can deduplicate by `eventId`.

After the bounded limit, the row is left durably `QUARANTINED`, with a sanitized
error and timestamp. It is not silently deleted or transformed into a new
business event. Authorized recovery is an explicit database operation that
returns the same record to `PENDING`; its event identity and payload remain
unchanged.

## Kafka Boundary

The Kafka adapter publishes to `ledger.posting.completed.v1` or
`ledger.posting.failed.v1` based on the persisted event type. Its record key is
the persisted `aggregateId`. Kafka ordering is guaranteed only for the same key
within the same topic; it is not guaranteed across completion and failure
topics. The adapter sets the governed headers `event-id`, `correlation-id`,
`causation-id`, `producer`, `schema-version`, and `occurred-at` from immutable
outbox data.

Kafka delivery is enabled only by runtime configuration. This repository does
not provision Kafka topics, Schema Registry subjects, credentials, or consumer
inboxes.

## Verification

Tests cover command metadata propagation, pre-decision rejection without a
failure event, durable failed-decision recording, PostgreSQL state transitions,
lease retry and quarantine behavior, and Kafka record key/header construction.
`./mvnw verify`, Helm lint/template validation, container build smoke, and
`git diff --check` are required before opening the PR.

# Ledger Outbox Delivery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver governed ledger outcome events reliably with immutable identity, bounded retry, and durable quarantine.

**Architecture:** Keep ledger entry creation and outbox insertion in one transaction. Add an append-only failure-decision source for the explicit internal failure path, then use a lease-based application worker plus a Kafka adapter to deliver already-persisted immutable events.

**Tech Stack:** Java 21, Spring Boot, Spring Data JPA, Flyway, PostgreSQL, Spring Kafka, JUnit 5, AssertJ, Mockito, Testcontainers.

**Spec:** `docs/superpowers/specs/2026-08-31-ledger-outbox-delivery-design.md`

## Global Constraints

- Target branch is `feature/101-ledger-events`; never modify or merge `main`.
- Ledger entries and event identity/payload are append-only.
- Legacy posting callers remain valid; `transactionId` and `reservationRequestId` are nullable and never synthesized.
- A failed event is emitted only after an append-only failure decision is durable.
- Delivery is at-least-once and retries retain the persisted `eventId`.
- Kafka key is `aggregateId`; ordering is per key and topic only.

---

### Task 1: Governed Metadata And Durable Failure Decisions

**Files:**
- Modify: `src/main/java/.../PostLedgerEntryCommand.java`, `PostLedgerReversalCommand.java`, web request/controller classes, event publisher classes
- Create: failure-decision input port/command and Flyway migration V6
- Test: `LedgerServiceTest`, `LedgerControllerTest`, `LedgerPersistenceIT`

- [x] **Step 1: Write failing tests** for nullable transaction/reservation metadata propagation, legacy omission, a durable failed decision producing one outbox row, and unbalanced input producing no failed event.
- [x] **Step 2: Run targeted tests** and confirm they fail because the command, port, and schema do not support the behavior.
- [x] **Step 3: Implement the minimum command/request, failure-decision, publisher, and migration changes.**
- [x] **Step 4: Run targeted unit and PostgreSQL tests** and confirm success.

### Task 2: Durable Delivery State Machine

**Files:**
- Modify: outbox entity/repository and migration V7
- Create: delivery repository/transport ports, application worker, configuration properties
- Test: application delivery tests and `LedgerPersistenceIT`

- [x] **Step 1: Write failing tests** for a claimed record becoming published, retry retaining its ID, bounded attempts quarantining, and an expired lease becoming claimable.
- [x] **Step 2: Run the tests** and confirm they fail because no delivery worker or state transitions exist.
- [x] **Step 3: Implement lease claims, retry scheduling, durable quarantine, and conditional scheduling.**
- [x] **Step 4: Run focused tests** and confirm the state machine passes.

### Task 3: Kafka Publisher Boundary And Operations

**Files:**
- Modify: `pom.xml`, `README.md`
- Create: Kafka transport adapter and `docs/outbox-delivery.md`
- Test: Kafka transport unit test

- [x] **Step 1: Write a failing Kafka adapter test** that asserts the governed topic, aggregate key, stable event ID, and all required headers.
- [x] **Step 2: Run the test** and confirm it fails because the adapter is absent.
- [x] **Step 3: Implement the conditional Kafka adapter and document state transitions, monitoring, quarantine recovery, and ordering.**
- [x] **Step 4: Run focused tests** and confirm the Kafka boundary passes.

### Task 4: Full Verification And Review

**Files:** all modified files

- [x] **Step 1: Run `./mvnw verify`** with Docker-enabled Testcontainers.
- [x] **Step 2: Run `helm lint helm --strict` and render the chart with SIT values.**
- [x] **Step 3: Build the container image and inspect the diff for secrets and unintended changes.**
- [ ] **Step 4: Run `git diff --check`, commit with a conventional message, push, and open a non-draft PR targeting `feature/101-ledger-events`.**

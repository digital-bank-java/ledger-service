# AGENTS.md

## Repository Purpose

`ledger-service` owns immutable journal entry posting and lookup.

It is the financial record service for debit/credit entries and should remain append-only at the business level.

## Current Responsibilities

- accept balanced ledger entries
- persist immutable ledger entries and lines
- retrieve ledger entries by id
- expose internal ledger APIs

## Current Non-Responsibilities

- direct customer-facing APIs
- account profile state
- naive account balance updates
- transaction saga coordination

## Architecture

- Hexagonal architecture
- Domain enforces entry invariants
- Application service orchestrates posting and retrieval
- Persistence adapter maps domain objects to PostgreSQL entities
- Web adapter exposes the internal HTTP API

## Key Commands

```bash
./mvnw test
./mvnw verify
./mvnw spring-boot:run
docker build -t digital-bank-java/ledger-service:<tag> .
helm lint helm --strict
```

## Runtime and Data

- Default service port: `8083`
- Logical database in SIT: `ledger_service`
- Runtime configuration comes from `config-repo`
- Schema is managed by Flyway

## Domain Rules

- Entries must remain balanced.
- There must be debit and credit lines.
- Persisted entries are immutable after creation.
- Future account effects should be driven by ledger completion and downstream event workflows.

## Testing Rules

- Unit tests cover domain/application behavior.
- Integration tests cover PostgreSQL persistence and HTTP API behavior.
- `verify` must stay green because it includes the Testcontainers-backed integration slice.

## Working Rules

- Do not add update or delete APIs for posted entries.
- Do not turn this repository into a generic balance service.
- Keep any future Kafka/event work explicit and tracked.

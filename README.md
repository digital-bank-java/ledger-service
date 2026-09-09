# Ledger Service

Ledger Service owns immutable, balanced financial journal entries for the Digital Bank Java platform. It currently supports internal ledger-entry posting and lookup backed by PostgreSQL; Kafka publication, reconciliation, and transaction-driven posting workflows remain planned.

## Responsibilities

- Persist immutable, balanced debit and credit ledger entries.
- Reject unbalanced entries and conflicting posting-request identifiers.
- Treat repeated equivalent requests as idempotent replays.
- Record append-only reversal entries without changing the original posting.
- Provide internal HTTP APIs for ledger-entry posting and lookup.
- Expose operational health endpoints.
- Load environment-specific configuration from Config Server.
- Publish OpenAPI metadata for the implemented internal HTTP APIs.

## Non-Responsibilities

- Customer profile management.
- Account balance projection ownership.
- Transfer orchestration.
- Kafka broker deployment or topic provisioning.

## Runtime Configuration

The service reads configuration from Config Server:

```properties
spring.config.import=configserver:${CONFIG_SERVER_URL:http://localhost:8888}
```

The configured service port is `8083`. SIT datasource settings and runtime identity are supplied by Config Server from `config-repo`; CI uses an isolated mock Config Server response.

## Internal Ledger Posting Contract

The ledger posting API is internal and is not a customer-facing balance-update API. A posting is accepted only when its debit and credit lines balance.

```http
POST /internal/v1/ledger-entries
```

- The first valid request returns `201 Created`.
- Repeating the same `postingRequestId` with the same normalized payload returns the original entry with `200 OK` and `Idempotent-Replay: true`.
- Reusing a `postingRequestId` with a different payload returns `409 Conflict` as Problem Details.
- Database uniqueness and transaction-scoped PostgreSQL advisory locking protect concurrent submissions.

Reversals are new immutable entries that swap the source entry's debit and credit lines:

```http
POST /internal/v1/ledger-entries/{ledgerEntryId}/reversals
```

The response contains `reversalOfLedgerEntryId`. The original entry is never updated or deleted. A missing source returns `404 Not Found`; repeated equivalent reversal requests replay the original reversal; conflicting reuse returns `409 Conflict`.

Error responses use `application/problem+json`. The generated internal contract is available at `/v3/api-docs` and includes the posting, replay, reversal, validation, conflict, and not-found responses.

## Prerequisites

- Java 21.
- Docker.
- Helm 4.
- Kubernetes access for SIT deployment validation.

The Maven Wrapper is included, so a global Maven installation is not required.

## Test And Quality Gate

```bash
./mvnw test
./mvnw verify
```

`./mvnw test` runs unit tests. `./mvnw verify` additionally runs integration tests, including PostgreSQL-backed Testcontainers checks.

## Run From A Workstation For Debugging

SIT is the supported lowest runtime environment. A workstation JVM is only a temporary debugging process connected to forwarded SIT dependencies; it is not a separate `local` profile or deployment environment.

Follow the shared [workstation debugging procedure](https://github.com/digital-bank-java/.github/blob/main/docs/workstation-debugging-against-sit.md). It covers scaling this deployment to zero, forwarding Config Server and PostgreSQL, supplying temporary synthetic SIT credentials, and restoring the deployment after debugging.

After exporting the documented overrides, start the service:

```bash
./mvnw spring-boot:run
```

Check health:

```bash
curl --fail http://localhost:8083/actuator/health
```

## Container

Build the image:

```bash
docker build -t digital-bank-java/ledger-service:0.0.2 .
```

Inspect the runtime user:

```bash
docker image inspect \
  --format '{{.Config.User}}' \
  digital-bank-java/ledger-service:0.0.2
```

Expected value:

```text
10001:10001
```

The runtime image contains no configuration repository or database credentials.

## Deploy To Local SIT

Validate the chart:

```bash
helm lint helm

helm template ledger-service helm \
  --values helm/values-sit.yaml \
  --namespace digital-bank-sit |
  kubectl apply --dry-run=client -f -
```

Deploy to local SIT after the image exists locally:

```bash
helm upgrade --install ledger-service helm \
  --namespace digital-bank-sit \
  --create-namespace \
  --values helm/values-sit.yaml \
  --wait \
  --timeout 5m
```

After deployment, verify the service locally through a temporary port-forward:

```bash
kubectl port-forward service/ledger-service 18083:8083 --namespace digital-bank-sit
curl --fail http://localhost:18083/actuator/health
curl --fail http://localhost:18083/v3/api-docs
```

Ledger APIs are currently internal service APIs. Do not add a public API Gateway route until an approved consumer and authorization policy exist.

## Outbox Delivery

Ledger outcomes are written to PostgreSQL in the same transaction as the
durable ledger decision. The delivery worker is disabled until Kafka runtime
configuration is supplied, then retries with a stable event ID and quarantines
records after its bounded attempt limit. The Kafka key is `aggregateId`, so
ordering applies per aggregate within a single topic only. See
[outbox delivery operations](docs/outbox-delivery.md) for the state machine,
configuration, monitoring query, and explicit replay procedure.

`transactionId` and `reservationRequestId` are required on internal posting and
reversal requests that create governed ledger events. They are persisted and
included in event payloads. The service never derives synthetic values from
correlation IDs or posting request IDs.

`LedgerPostingFailed.v1` is recorded only through the internal durable
failure-decision input port. Validation, authorization, malformed or
unbalanced requests, missing source entries, and infrastructure failures that
occur before a durable decision do not emit a financial failure event.

## Kafka Direction

Kafka is shared platform infrastructure, not part of the Ledger Service container.

When `ledger.posting.consumer.enabled=true`, Ledger consumes the governed
`ledger.posting.requested.v1` command topic through a dedicated listener
container. A record that cannot be parsed or processed after two bounded
one-second retries is published unchanged to
`ledger.posting.requested.v1.dlq`, retaining its Kafka key, partition, and
diagnostic error headers. The recovered offset is committed so a poison record
cannot block the partition indefinitely. Valid commands that are rejected by
ledger business rules continue through the durable `LedgerPostingFailed.v1`
failure-decision path instead of being quarantined as malformed messages.

### SIT Acceptance Failure Fixture

The Kafka consumer has a disabled-by-default, deployment-only SIT acceptance
fixture for the compensation scenario. It has no HTTP route. Its only active
Spring profile must be `sit`. Set both process environment variables
`LEDGER_POSTING_ACCEPTANCE_FIXTURE_ENABLED=true` and
`LEDGER_POSTING_ACCEPTANCE_FIXTURE_POSTING_REQUEST_ID` to a nonblank value;
the latter must match the bound request ID exactly. Configuration from Config
Server or application properties alone cannot activate the fixture. It matches
that exact posting request ID and records the normal governed
`LedgerPostingFailed.v1` decision with `INTERNAL_ERROR`, including the inbox
and outbox records. Startup rejects an enabled fixture if any of these safety
conditions is not satisfied. Restore both overrides immediately after the one
acceptance case completes.

For local SIT, Kafka is deployed as shared infrastructure in `digital-bank-sit` and owned by `infra-sit`. For AWS UAT and production, the preferred direction is a managed Kafka service, such as Amazon MSK, connected privately to Kubernetes workloads.

Kafka topic provisioning, Schema Registry subjects, consumer inboxes, and
reconciliation remain platform or consumer responsibilities; they are not
implied by the presence of the Kafka broker.

## Security, Promotion, And Contribution

The SIT deployment uses an internal `ClusterIP` Service, a non-root container, a read-only root filesystem, bounded writable temporary storage, and credentials supplied from Kubernetes Secrets. Do not commit credentials, tokens, or production endpoints.

The same artifact is intended to move through `sit`, `uat`, and `prod` without rebuilding. UAT and production infrastructure will use managed services and controlled secret delivery.

Pull requests and changes to `main` run Maven verification, Helm lint/rendering, and a container smoke test. Use a tracked issue, dedicated branch, and pull request for each change. See the organization [README standard](https://github.com/digital-bank-java/.github/blob/main/docs/readme-standard.md) and [platform conventions](https://github.com/digital-bank-java/.github/blob/main/docs/platform-conventions.md).

## Operational Logging

The service emits one-line ECS JSON console events and propagates the bounded
`X-Correlation-ID` boundary defined in the organization [structured logging and redaction contract](https://github.com/digital-bank-java/.github/blob/main/docs/structured-logging-and-redaction.md). Request bodies, credentials, tokens, ledger data, and customer data are not logged.

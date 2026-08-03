# Ledger Service

Ledger Service owns immutable, balanced financial journal entries for the Digital Bank Java platform. It currently supports internal ledger-entry posting and lookup backed by PostgreSQL; Kafka publication, reconciliation, and transaction-driven posting workflows remain planned.

## Responsibilities

- Persist immutable, balanced debit and credit ledger entries.
- Reject unbalanced entries and conflicting posting-request identifiers.
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
docker build -t digital-bank-java/ledger-service:0.0.1 .
```

Inspect the runtime user:

```bash
docker image inspect \
  --format '{{.Config.User}}' \
  digital-bank-java/ledger-service:0.0.1
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

## Kafka Direction

Kafka is shared platform infrastructure, not part of the Ledger Service container.

For local SIT, Kafka is deployed as shared infrastructure in `digital-bank-sit` and owned by `infra-sit`. For AWS UAT and production, the preferred direction is a managed Kafka service, such as Amazon MSK, connected privately to Kubernetes workloads.

Ledger entry persistence is the current implementation. Outbox/inbox handling, event schemas, and Kafka publication must be added with explicit idempotency and reconciliation requirements; they are not implied by the presence of the Kafka broker.

## Security, Promotion, And Contribution

The SIT deployment uses an internal `ClusterIP` Service, a non-root container, a read-only root filesystem, bounded writable temporary storage, and credentials supplied from Kubernetes Secrets. Do not commit credentials, tokens, or production endpoints.

The same artifact is intended to move through `sit`, `uat`, and `prod` without rebuilding. UAT and production infrastructure will use managed services and controlled secret delivery.

Pull requests and changes to `main` run Maven verification, Helm lint/rendering, and a container smoke test. Use a tracked issue, dedicated branch, and pull request for each change. See the organization [README standard](https://github.com/digital-bank-java/.github/blob/main/docs/readme-standard.md) and [platform conventions](https://github.com/digital-bank-java/.github/blob/main/docs/platform-conventions.md).

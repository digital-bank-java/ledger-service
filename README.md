# Ledger Service

Ledger Service will own immutable financial postings for the Digital Bank Java platform.

This bootstrap slice establishes the deployable service foundation only. Ledger posting commands, journal entries, Kafka publication, and reconciliation behavior will be implemented in later stories.

## Responsibilities

- Provide the future home for immutable debit and credit ledger postings.
- Expose operational health endpoints.
- Load environment-specific configuration from Config Server.
- Publish OpenAPI metadata for future HTTP APIs.

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

The default service port is expected to be provided by Config Server. CI uses a mock Config Server response with `server.port=8083`.

## Prerequisites

- Java 21.
- Docker.
- Helm 4.
- Kubernetes access for SIT deployment validation.

## Run From A Workstation For Debugging

Run tests:

```bash
./mvnw test
```

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

## Helm

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

## Kafka Direction

Kafka is shared platform infrastructure, not part of the Ledger Service container.

For local SIT, Kafka should be deployed into a shared infrastructure namespace and managed from the local infrastructure repository. For AWS UAT and production, the preferred direction is a managed Kafka service, such as Amazon MSK, connected privately to the Kubernetes workloads.

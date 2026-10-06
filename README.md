# Investment Platform Sim

[![CI](https://github.com/tdutra-dev/investment-platform-sim/actions/workflows/ci.yml/badge.svg)](https://github.com/tdutra-dev/investment-platform-sim/actions/workflows/ci.yml)

A portfolio project that simulates an investment platform using an event-driven microservices architecture. It provides APIs for customer management and transaction processing, and records business events in an audit log.

• Built three Spring Boot services: Customer Service and Transaction Service with MySQL, plus an Audit Log Service with MongoDB.

• Implemented a Transactional Outbox pattern to reliably publish customer and transaction events to Kafka, with retries, exponential backoff, and idempotent event handling.

• Added Keycloak-based OAuth2/JWT authentication with scope-based authorization, and exposed health checks and Prometheus metrics.

• Containerized the services and supporting infrastructure with Docker Compose; added unit, integration, and end-to-end tests using Testcontainers, and automated verification with GitHub Actions.

Technologies: Java 17, Spring Boot, Spring Security, Spring Data JPA, MySQL, Kafka, MongoDB, Keycloak, Docker Compose, Testcontainers, Maven, GitHub Actions.

## Architecture

| Service | Port | DB | Role |
|---|---|---|---|
| `customer-service` | 8081 | MySQL | Customer management (DDD: `Customer` is the aggregate root), publishes `customer-events` via outbox |
| `transaction-service` | 8082 | MySQL | Transaction processing (business rule: no negative balance), publishes `transaction-events` via Transactional Outbox |
| `audit-log-service` | 8083 | MongoDB | Kafka consumer of both topics, idempotent event log, query API |

```mermaid
flowchart LR
    API([POST /api/transactions]) --> TS[transaction-service]
    TS -- "1 tx: business row + outbox row" --> DB[(MySQL)]
    DB -- "pending rows" --> SCH[Outbox scheduler]
    SCH -- "publish, then mark sent" --> K{{Kafka<br/>transaction-events}}
    K --> AL[audit-log-service]
    AL -- "insert, dedup by eventId" --> M[(MongoDB)]
```

`customer-service` follows the same outbox flow on the `customer-events` topic.

## Design decisions

- **Transactional Outbox, no dual write.** The business row and the event row are written in the same DB transaction; a scheduler publishes pending rows to Kafka and marks them as sent only after the broker ack. Nothing is sent to Kafka from inside the business transaction, so a crash can never leave a committed change without its event (or vice versa). Delivery is at-least-once.
- **MongoDB for the audit log.** Events are append-only, schema-flexible documents that are stored verbatim and queried by `aggregateId`; no joins or relational constraints are needed.
- **Idempotent consumer.** Every event carries an `eventId`, which the audit-log service uses as the document `_id`. Redelivered events hit a duplicate key and are ignored, so at-least-once delivery produces exactly one audit entry.

## Outbox strategy

Both `transaction-service` and `customer-service` use the shared `outbox-common` module.

- **Claiming:** each scheduler run opens one transaction and claims a batch with `SELECT ... FOR UPDATE SKIP LOCKED` (MySQL 8.4). Several instances can run against the same table without publishing a row twice: a row locked by one instance is skipped by the others.
- **States:** `PENDING` -> `SENT` after the broker ack. A failed publish increments `attempts`, stores `last_error` and sets `next_attempt_at` with exponential backoff; after the max attempts the row becomes `FAILED` (kept for inspection, never retried automatically).
- **Cleanup:** a second job deletes `SENT` rows older than the retention period.
- **Configuration** (per service, `application.properties`): `outbox.batch-size` (50), `outbox.max-attempts` (5), `outbox.backoff.initial` (1s), `outbox.backoff.max` (5m), `outbox.retention` (7d), `outbox.publisher.fixed-delay-ms` (5000), `outbox.cleanup.fixed-delay-ms` (1h).
- **Remaining limits:** delivery is at-least-once (a crash after the broker ack but before the commit re-sends the row; consumers deduplicate by `eventId`); there is no ordering guarantee across topics, and none across rows once retries are involved; a publish failure stops the current batch.

## Stack

Java 17, Spring Boot 3.3, Spring Data JPA (MySQL 8.4), Spring Data MongoDB (MongoDB 7), Spring Kafka, JUnit 5, Mockito, Testcontainers.

## Quick start

Requirements: Docker (and JDK 17 + Maven 3.9+ to build and test locally).

**Option A: whole stack in containers**

```bash
docker compose --profile app up -d --build   # MySQL, MongoDB, Kafka + the three services (multi-stage images, non-root, healthchecks)
docker compose --profile app ps              # wait until all show "healthy"
```

**Option B: infrastructure in containers, services from the jars**

```bash
# 1. Infrastructure only: MySQL, MongoDB, Kafka
docker compose up -d

# 2. Build all modules and run all tests (unit + Testcontainers + end-to-end).
#    Needs Docker running; takes a few minutes (container startup + end-to-end test).
mvn clean verify

# 3. Run the services (one terminal each)
java -jar customer-service/target/customer-service-0.0.1-SNAPSHOT.jar
java -jar transaction-service/target/transaction-service-0.0.1-SNAPSHOT.jar
java -jar audit-log-service/target/audit-log-service-0.0.1-SNAPSHOT.jar
```

Try it (works with either option). All endpoints require a bearer token; get one from Keycloak first (see [Security](#security)):

```bash
TOKEN=$(curl -s -X POST http://localhost:8180/realms/investment/protocol/openid-connect/token \
  -d grant_type=password -d client_id=platform-user-client -d username=demo -d password=demo \
  -d 'scope=customers:read customers:write transactions:read transactions:write audit:read' \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["access_token"])')

# customer-service
curl -X POST localhost:8081/api/customers -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Ada Lovelace","email":"ada@example.com"}'

# transaction-service (use the customer id returned above)
curl -X POST localhost:8082/api/transactions -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"customerId":"<customer-id>","amount":250.00,"currency":"EUR","type":"DEPOSIT"}'

# audit-log-service (use the transaction id or customer id; allow ~5 s for the outbox scheduler)
curl -H "Authorization: Bearer $TOKEN" 'localhost:8083/api/audit-log?aggregateId=<id>'
```

Other endpoints: `GET /api/customers/{id}`, `GET /api/transactions/{id}`, `GET /api/transactions?customerId=...`.

Host ports are configurable. If a local MySQL, MongoDB or Kafka already uses a default port, copy `.env.example` to `.env`, change `MYSQL_PORT`, `MONGO_PORT` or `KAFKA_PORT`, and export the same variables before starting the services (`set -a; source .env; set +a`). `docker compose` reads `.env` automatically.

## Security

Keycloak (realm `investment`, imported from `keycloak/investment-realm.json`) runs in `docker compose` on port `8180` (`KEYCLOAK_PORT`). Each service is an OAuth2 resource server (`spring-boot-starter-oauth2-resource-server`): the JWT signature (JWKS), the issuer and the audience (the service name) are validated by the shared `security-common` module. Kafka and internal flows are not authenticated.

| Endpoint | Required scope |
|---|---|
| `POST /api/customers` | `customers:write` |
| `GET /api/customers/{id}` | `customers:read` |
| `POST /api/transactions` | `transactions:write` |
| `GET /api/transactions`, `GET /api/transactions/{id}` | `transactions:read` |
| `GET /api/audit-log` | `audit:read` |

No token gives `401`, a token without the right scope gives `403`. Health probes (`/actuator/health/**`) are public.

Clients in the realm: `platform-user-client` (public, password grant, user `demo` / `demo`) and `platform-service-client` (secret `platform-service-secret`, client credentials). Scopes are optional, so a token only contains the scopes it asks for. The credentials are for local development only.

```bash
# client credentials flow
curl -s -X POST http://localhost:8180/realms/investment/protocol/openid-connect/token \
  -d grant_type=client_credentials -d client_id=platform-service-client -d client_secret=platform-service-secret \
  -d 'scope=audit:read'

curl -i localhost:8083/api/audit-log?aggregateId=x                                  # 401
curl -i -X POST localhost:8081/api/customers -H "Authorization: Bearer $AUDIT_ONLY_TOKEN"   # 403
```

Containers validate the issuer `http://localhost:8180/realms/investment` (the URL clients use) while fetching keys from `http://keycloak:8080`. When running the jars outside Docker, the defaults already point at `localhost:8180`.

## Observability

Each service exposes (through Spring Boot Actuator and Micrometer):

| Endpoint | Purpose |
|---|---|
| `/actuator/health/liveness`, `/actuator/health/readiness` | Kubernetes-style probes (public, no details) |
| `/actuator/prometheus` | Prometheus scrape endpoint (public in this demo; protect it at the network level in a real deployment) |

Custom outbox metrics (customer-service and transaction-service, tag `outbox=<table name>`):

| Metric | Type | Meaning |
|---|---|---|
| `outbox_pending` | gauge | rows waiting to be published (`PENDING`) |
| `outbox_publish_success_total` | counter | events acknowledged by the broker |
| `outbox_publish_failure_total` | counter | failed publish attempts (retried with backoff) |

All metrics carry an `application` tag. Log lines include `[eventId=...]` while an event is being published or consumed, so one event can be followed across services with `grep <eventId>`. There is no Grafana, tracing or log shipping stack.

```bash
curl -s localhost:8082/actuator/prometheus | grep outbox_
```

## Tests

- Unit tests (JUnit 5 + Mockito): domain and service logic, outbox scheduler, idempotent consumer.
- Observability tests: public health probes and Prometheus metrics on all three services, plus outbox metrics on both publishers.
- MockMvc security tests: 401 / 403 / 2xx per endpoint using the `jwt()` post-processor.
- Testcontainers integration tests: MySQL repository, outbox → Kafka round trip, two concurrent outbox instances publishing every event exactly once, MongoDB repository.
- End-to-end (`e2e-tests`, failsafe): starts the three packaged services against MySQL, Kafka, MongoDB and Keycloak containers, obtains a real token from Keycloak (client credentials), checks 401/403, creates a customer and a transaction over REST and asserts both events are stored in MongoDB.

## Known trade-offs

At-least-once delivery, made safe by idempotency via `eventId`; no ordering guarantee across topics.

## Limitations and next steps

- `FAILED` outbox rows are not retried automatically and there is no alerting or admin endpoint for them yet.
- Keycloak runs in dev mode with a committed demo realm; a real deployment needs its own realm, secrets and TLS.
- No tracing, dashboards or alerting; metrics are exposed but not scraped by anything in the compose file.

## Development status

| Phase | Description | Status |
|---|---|---|
| 0 | Scaffolding: 3 Spring Boot services + Docker Compose | ✅ Done |
| 1 | `customer-service`: domain, repository, service, REST, unit tests | ✅ Done |
| 2 | `transaction-service`: domain with business rule, REST, unit tests | ✅ Done |
| 3 | Transactional Outbox + Kafka publisher (transaction and customer events) | ✅ Done |
| 4 | `audit-log-service`: Kafka consumer, idempotent MongoDB persistence, query API | ✅ Done |
| 5 | Testcontainers integration tests (MySQL, Kafka, MongoDB) | ✅ Done |
| 6 | End-to-end Testcontainers test + CI | ✅ Done |
| 7 | Outbox hardening: batch claiming with `SKIP LOCKED`, retry with backoff, cleanup, shared `outbox-common` module | ✅ Done |
| 8a | Dockerfiles and `app` compose profile | ✅ Done |
| 8b | OAuth2 / OIDC authentication (Keycloak, JWT resource servers, scope-based authorization) | ✅ Done |
| 8c | Observability: Actuator health probes, Prometheus metrics, outbox metrics, eventId in logs | ✅ Done |

# Investment Platform Sim

Simulazione di una piattaforma di investimenti con architettura a microservizi event-driven.

## Architettura

| Servizio | Porta | DB | Ruolo |
|---|---|---|---|
| `customer-service` | 8081 | MySQL | Customer Management (DDD Aggregate Root: Customer) |
| `transaction-service` | 8082 | MySQL | Transaction Processing + Outbox Pattern |
| `audit-log-service` | 8083 | MongoDB | Consumer Kafka → Event Log |

Comunicazione asincrona via **Apache Kafka** (topic: `transaction-events`, `customer-events`).

## Stack

- Java 17 + Spring Boot 3.3.x
- Spring Data JPA (MySQL 8) — customer-service, transaction-service
- Spring Data MongoDB — audit-log-service
- Spring Kafka
- Docker + Docker Compose
- JUnit 5 + Mockito + Testcontainers

## Quick Start

### 1. Avvia l'infrastruttura

```bash
docker compose up -d
```

### 2. Compila tutti i moduli

```bash
mvn clean install
```

### 3. Avvia i servizi (3 terminali separati)

```bash
# Terminale 1
cd customer-service && mvn spring-boot:run

# Terminale 2
cd transaction-service && mvn spring-boot:run

# Terminale 3
cd audit-log-service && mvn spring-boot:run
```

## Fasi di sviluppo

| Fase | Descrizione | Stato |
|---|---|---|
| 0 | Scaffolding — 3 Spring Boot projects + Docker Compose | ✅ |
| 1 | `customer-service` completo (domain, repository, service, controller, test) | 🔜 |
| 2 | `transaction-service` — domain con regola di business, CRUD, test | 🔜 |
| 3 | Outbox Pattern in `transaction-service` — scheduler + producer Kafka | 🔜 |
| 4 | `audit-log-service` — consumer Kafka, persistenza Mongo, endpoint query | 🔜 |
| 5 | Testcontainers su tutti e tre i servizi | 🔜 |
| 6 | Verifica end-to-end | 🔜 |

## API REST (overview)

**customer-service**
- `POST /api/customers`
- `GET  /api/customers/{id}`

**transaction-service**
- `POST /api/transactions`
- `GET  /api/transactions/{id}`
- `GET  /api/transactions?customerId=...`

**audit-log-service**
- `GET  /api/audit-log?aggregateId=...`

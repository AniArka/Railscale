# RailScale — Distributed Railway Seat Booking Platform

[![Java 21/25](https://img.shields.io/badge/Java-21%2F25-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2024.0.0-blue.svg)](https://spring.io/projects/spring-cloud)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-7.2-red.svg)](https://redis.io/)
[![Apache Kafka](https://img.shields.io/badge/Kafka-3.9-black.svg)](https://kafka.apache.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

RailScale is a high-concurrency, distributed railway reservation system designed to withstand extreme booking contention (e.g., Tatkal flash surges) while guaranteeing **zero overselling**, **zero circular deadlocks**, and **absolute financial and inventory consistency**.

---

## Architecture Highlights

- **Zero Overselling Guarantee:** Enforced at the database layer via PostgreSQL pessimistic write locks (`SELECT ... FOR UPDATE`) with canonical UUID sorting.
- **Dual-Layer Concurrency Control:** High-speed Redis distributed pre-locks (Redlock with Lua token validation) to shed excess contention at memory speed, backed by PostgreSQL row-level locks.
- **Circular Wait Elimination:** All multi-seat reservation requests sort inventory UUIDs ascending before locking, mathematically eliminating deadlocks.
- **Zero Dual-Writes (Transactional Outbox):** State changes and domain event publishing are committed atomically in the same local database transaction. The outbox engine uses a leaderless 30-second lease with automatic peer crash reclaim.
- **Graceful Degradation:** If Redis becomes unavailable, the booking engine seamlessly falls back to PostgreSQL row locks; if Kafka becomes unavailable, events buffer safely in the `outbox_events` table and drain upon reconnection.
- **No Phantom Confirmations (Invariant 9):** Late payments arriving after hold/payment deadline expiration never resurrect expired bookings; released seats remain available and an automated refund event is triggered.
- **Full Observability Stack:** Micrometer metrics scraped by Prometheus, pre-configured Grafana dashboards, OpenTelemetry distributed tracing with Zipkin, and structured JSON logging.

---

## Microservices Topology

| Service | Port | Description |
|---|---|---|
| **API Gateway** | `8080` | Spring Cloud Gateway, JWT verification, rate limiting, circuit breaker |
| **User Service** | `8081` | Authentication, JWT issuance, profile management |
| **Search Service** | `8082` | Train/route discovery, live seat availability, multi-tier caching |
| **Booking Service** | `8083` | Reservation engine, hold/confirmation lifecycle, outbox publisher, reconciler |
| **Payment Service** | `8084` | Payment processing, webhook simulation, automated refunds |
| **Notification Service** | `8085` | Asynchronous email/SMS event consumers with idempotency gate |
| **Eureka Discovery** | `8761` | Dynamic microservice discovery registry |

---

## Technology Stack

- **Language & Frameworks:** Java 21 / 25, Spring Boot 3.5.5, Spring Cloud 2024.0.0
- **Primary Database:** PostgreSQL 16 (Source of Truth, ACID transactions, Row-Level Locking)
- **Cache & Distributed Locks:** Redis 7.2 (Lettuce client, Spring Data Redis)
- **Event Streaming:** Apache Kafka 3.9 (Transactional Outbox, Manual ACKs, DLQ)
- **Resilience:** Resilience4j (Circuit Breakers, Bounded Retries, Timeouts)
- **Observability:** Micrometer, Prometheus, Grafana, OpenTelemetry, Zipkin, Logstash Logback
- **Testing & Chaos:** JUnit 5, Mockito, AssertJ, H2, k6, PowerShell Automation

---

## Quickstart & Setup

### 1. Prerequisites
- **JDK 21 or JDK 25** (`JAVA_HOME` set)
- **Maven 3.9+** (or IntelliJ bundled Maven)
- **Docker & Docker Compose** (for multi-service deployment)

### 2. Build the System
Build all modules without running tests:
```powershell
mvn clean install -DskipTests
```

### 3. Run with Docker Compose
Start the full infrastructure and services (PostgreSQL, Redis, Kafka, Eureka, Zipkin, Prometheus, Grafana, Microservices):
```powershell
docker-compose up -d
```

Verify all containers are healthy:
```powershell
docker ps
```

### 4. Running the Test Suite
Run the full in-memory Spring Boot integration test suite:
```powershell
mvn test
```

Specific test suites:
```powershell
# Reservation engine concurrency & invariants
mvn test -pl railscale-booking-service -Dtest=ReservationEngineTest

# Multi-seat canonical deadlock prevention chaos test
mvn test -pl railscale-booking-service -Dtest=DeadlockPreventionTest

# Outbox publisher crash and lease expiration reclaim chaos test
mvn test -pl railscale-booking-service -Dtest=OutboxPublisherCrashRecoveryTest

# Payment failure, abandonment, and late payment resilience chaos test
mvn test -pl railscale-booking-service -Dtest=PaymentFailureResilienceTest

# 30-concurrent user load test with zero overselling verification
mvn test -pl railscale-booking-service -Dtest=LoadTestingVerificationTest
```

---

## Load & Chaos Testing

### Load Testing with k6
Automated high-concurrency benchmarks (`high_contention`, `end_to_end_booking`, `progressive_load`):
```powershell
# Seed deterministic test catalog data
psql -h localhost -U railscale -d railscale_booking -f tests/load/seed-data.sql

# Execute benchmark harness
.\tests\load\run_benchmarks.ps1 -Scenario high_contention -VUs 1000 -Duration 30s
```

### Chaos Engineering Automation
Inject component failures during peak traffic:
```powershell
# Run all chaos scenarios (Booking crash, Redis failure, Kafka broker failure)
.\tests\chaos\run_chaos.ps1 -Scenario all
```

### Post-Test Invariant Verification
Mathematically verify zero overselling, zero orphaned holds, and metadata integrity in PostgreSQL:
```powershell
psql -h localhost -U railscale -d railscale_booking -f tests/load/verify-invariants.sql
```

---

## Observability Dashboards

Once Docker Compose is running, access the observability consoles:

| Console | URL | Default Credentials |
|---|---|---|
| **Grafana Booking Dashboard** | [http://localhost:3000](http://localhost:3000) | `admin` / `admin` |
| **Prometheus Metrics Scraper** | [http://localhost:9090](http://localhost:9090) | *(None)* |
| **Zipkin Distributed Tracing** | [http://localhost:9411](http://localhost:9411) | *(None)* |
| **Eureka Service Registry** | [http://localhost:8761](http://localhost:8761) | *(None)* |

---

## Documentation Index

Comprehensive architectural specifications and designs are available in the [`docs/`](docs/) directory:

- [System Architecture & Microservices Topology](docs/architecture.md)
- [REST API Specification & Contract Guidelines](docs/api-spec.md)
- [Database Design, Relational Schema & ERD](docs/database-design.md)
- [Data Consistency Model & Dual-Write Mitigation](docs/consistency.md)
- [Concurrency Control & Deadlock Elimination](docs/concurrency.md)
- [Kafka & Event-Driven Architecture](docs/kafka-design.md)
- [Failure Recovery, Resilience & Reconciliation](docs/failure-recovery.md)
- [Performance Benchmarking & KPI Targets](docs/benchmarks.md)

---

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.

# RailScale Performance Benchmarks (Section 51)

> **Mandate**: Never invent benchmark numbers. The README and documentation must only record empirical measurements under documented environments.

---

## 1. Benchmark Execution Methodology

RailScale performance benchmarks evaluate progressive concurrency and high-contention seat booking invariants across 4 distinct load tiers:

| Tier | Concurrency (VUs) | Target Scenario | Primary Objective |
|:---:|:---:|:---|:---|
| **Tier 1** | **100** | Warm-up & baseline | Baseline latency (p50/p95) and connection pool sizing |
| **Tier 2** | **1,000** | Normal busy hour | Gateway routing, Redis lock efficiency, outbox publisher throughput |
| **Tier 3** | **10,000** | Tatkal rush / surge | P99 latency bounds, PostgreSQL row-lock contention, circuit breaker thresholds |
| **Tier 4** | **50,000+** | Extreme contention spike | Anti-stampede protection, rate limiter activation, zero overselling |

---

## 2. Environment Specification Record

Every benchmark run must document the environment configuration:

- **Host Hardware**: CPU architecture, core count, clock frequency, RAM capacity
- **Operating System**: Kernel version, socket limits (`ulimit -n`)
- **Database Configuration**:
  - PostgreSQL 16 (max connections: 200, shared buffers: 512MB, work mem: 16MB)
- **Redis Configuration**:
  - Redis 7 Alpine (maxmemory: 2GB, eviction: volatile-lru)
- **Kafka Configuration**:
  - 1 broker, 3 partitions per topic, replication factor 1
- **Service Topology**:
  - API Gateway: 1 instance (reactive Netty)
  - Booking Service: 1-2 instances (Spring Boot 3.5.5, HikariCP pool: 50)
  - Service Discovery: Eureka Server (port 8761)
- **Load Generator**: k6 (v0.50+)

---

## 3. Results Matrix Template

| Load (VUs) | Throughput (req/s) | p50 (ms) | p95 (ms) | p99 (ms) | Error Rate (%) | Overselling Violations |
|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| **100** | *[measured]* | *[measured]* | *[measured]* | *[measured]* | *[measured]* | **0** |
| **1,000** | *[measured]* | *[measured]* | *[measured]* | *[measured]* | *[measured]* | **0** |
| **10,000** | *[measured]* | *[measured]* | *[measured]* | *[measured]* | *[measured]* | **0** |
| **50,000** | *[measured]* | *[measured]* | *[measured]* | *[measured]* | *[measured]* | **0** |

---

## 4. Architectural Invariant Assertions

After every benchmark run, execute `tests/load/verify-invariants.sql` against PostgreSQL:

```bash
# Verify invariants
docker exec -i railscale-postgres psql -U railscale -d railscale < tests/load/verify-invariants.sql
```

Verification assertions:
1. **Zero Overselling**: No single `inventory_id` has more than 1 `CONFIRMED` booking.
2. **Metadata Integrity**: Every `HELD` seat has non-null `held_by` and `held_until`.
3. **Idempotency**: No duplicate `(user_id, idempotency_key)` exists in the database.

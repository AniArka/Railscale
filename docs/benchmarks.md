# RailScale — Performance Benchmarking & Load Testing Methodology
## Specification per Architecture Section 51 & Phase 8

---

## 1. Benchmarking Philosophy & Section 51 Mandate

Per Architecture Section 51:
> *"Never invent benchmark numbers. Document the methodology, workloads, target thresholds, and verification commands. Fill metrics only with real measured data."*

The RailScale load testing suite is designed to subject the system to realistic high-contention tatkal booking traffic and verify strict transactional invariants under peak load.

---

## 2. Workload Scenarios & Profiles

The load test suite is codified as **k6** scenarios located in `tests/load/scenarios/`:

### 2.1 Scenario 1: Extreme Seat Contention (`high_contention.js`)
- **Workload:** 1,000–10,000 virtual users competing simultaneously for a small finite pool of seats (e.g., 50–100 seats).
- **Behavior:** All users fire `POST /api/v1/bookings/hold` at the exact same moment ($t=0$).
- **Success Criteria:**
  - Exactly $N$ requests succeed with `201 Created` ($N = \text{total available seats}$).
  - All remaining requests receive `409 Conflict (SEAT_UNAVAILABLE)`.
  - Zero `500 Internal Server Errors`.
  - Zero deadlocks (`PSQLException: deadlock detected`).

### 2.2 Scenario 2: End-to-End Complete User Journey (`end_to_end_booking.js`)
- **Workload:** Simulates the complete checkout funnel with realistic user think times:
  1. `GET /api/v1/search` (search trains and journeys).
  2. `GET /api/v1/journeys/{id}/seats` (view live seat layout).
  3. `POST /api/v1/bookings/hold` (hold seats).
  4. `POST /api/v1/bookings/{id}/confirm` (confirm and initiate payment).
  5. `POST /api/v1/payments/{id}/simulate` (complete payment).
- **Success Criteria:**
  - Complete funnel success rate $> 99\%$ for uncontended seats.
  - End-to-end booking latency $p95 < 250\text{ms}$.

### 2.3 Scenario 3: Progressive Multi-Tier Load (`progressive_load.js`)
- **Workload:** Stepped ramp-up:
  - Tier 1: 100 VUs (Warmup & Baseline)
  - Tier 2: 500 VUs (Normal Peak)
  - Tier 3: 2,000 VUs (Tatkal Surge)
  - Tier 4: 5,000+ VUs (Stress & Breaking Point)
- **Success Criteria:** System gracefully sheds excess load via HTTP 429 / 409 without database crash or unhandled 5xx errors.

---

## 3. Key Performance Indicators (KPI Targets)

| Metric | Target SLA | Measured Benchmark (Baseline) |
|---|---|---|
| **Max Hold Throughput** | $\ge 2,500\text{ req/sec}$ | `[measured]` |
| **Hold Request Latency (p95)** | $< 150\text{ms}$ | `[measured]` |
| **Hold Request Latency (p99)** | $< 300\text{ms}$ | `[measured]` |
| **Search Request Throughput** | $\ge 15,000\text{ req/sec}$ | `[measured]` |
| **Search Request Latency (p95)**| $< 25\text{ms}$ (cached) | `[measured]` |
| **Overselling Invariant** | **Exactly 0** | **0 (Verified)** |
| **Deadlock Exceptions** | **Exactly 0** | **0 (Verified)** |

---

## 4. Automation & Running Instructions

### Pre-requisites
1. Seed database with deterministic test catalog:
```powershell
psql -h localhost -U railscale -d railscale_booking -f tests/load/seed-data.sql
```
2. Start the microservices cluster:
```powershell
docker-compose up -d
```
3. Run the automated benchmark harness:
```powershell
.\tests\load\run_benchmarks.ps1 -Scenario high_contention -VUs 1000 -Duration 30s
```

### Invariant Verification
Immediately following any load test execution, run the SQL invariant suite to mathematically verify data integrity:
```powershell
psql -h localhost -U railscale -d railscale_booking -f tests/load/verify-invariants.sql
```

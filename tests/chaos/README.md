# RailScale Chaos Engineering & Fault Injection Suite

This directory contains the chaos engineering automation and documentation for **RailScale** as specified in **Architecture Section 52** and **Phase 9**.

---

## 1. Objectives & Guarantees

Under peak concurrency and random component crashes, RailScale enforces the following invariant guarantees:

1. **Zero Overselling (Invariant 1 & 2):** Never sell the same physical seat more than once for a given journey leg, regardless of how many booking instances crash or fail over.
2. **Deterministic Deadlock Prevention (Section 9):** Requests locking multiple seats sort seat UUIDs canonically before locking, eliminating circular wait deadlocks.
3. **Outbox Durability (Section 12):** If a booking instance crashes mid-publication, its 30-second outbox lease expires and a peer instance safely reclaims and publishes pending events without loss.
4. **Graceful Redis Degradation (Section 18 & 19):** If Redis becomes unreachable, the booking service gracefully falls back to PostgreSQL pessimistic write locks (`SELECT ... FOR UPDATE`) and continues serving bookings.
5. **Eventual Event Consistency During Kafka Downtime:** If Kafka crashes, booking transactions commit normally because events are written transactionally to PostgreSQL's `outbox_events` table. Buffered events drain automatically once Kafka reconnects.
6. **No Phantom Confirmations on Late Payments (Invariant 9 & Section 10):** A successful payment arriving after hold/payment deadline expiration never resurrects an expired booking. The system retains the `EXPIRED` status, keeps released seats available for other users, and emits a `RefundRequired` event.

---

## 2. Failure Injection Matrix

| Target Component | Failure Injected | Detection Mechanism | Fallback / Resilience Path | Recovery Behavior |
|---|---|---|---|---|
| **Booking Instance** | Process kill (`SIGKILL` / `docker kill`) | Eureka heartbeat timeout (30s) / Gateway circuit breaker | Gateway routes traffic to healthy instance | Peer instance reclaims expired outbox leases after 30s |
| **Redis Cache / Locks** | Container stop (`docker stop railscale-redis`) | Lettuce connection exception, RedisHealthIndicator | Fall back directly to PostgreSQL row-level locks | Auto-reconnect via Spring Data Redis connection pool |
| **Kafka Broker** | Container stop (`docker stop railscale-kafka`) | KafkaProducer connection timeout | Outbox table persists events as `PENDING` in PostgreSQL | OutboxPublisher drains buffered events upon reconnection |
| **Payment Gateway** | Simulated failure / timeout webhook | PaymentEventConsumer / Scheduled Reconciliation | Booking marked `FAILED`, seats immediately released to `AVAILABLE` | Automatic lease expiration via `ReservationReconciliationService` |

---

## 3. Test Suites

### A. In-Memory Integration Tests (Automated CI / Local)
Since these run inside the Spring Boot integration test lifecycle, they run deterministically on every build without requiring external Docker infrastructure:

- **`OutboxPublisherCrashRecoveryTest.java`**: Simulates worker process crash during outbox event publication, verifies lease timeout, and validates secondary instance takeover with zero duplicate publishes.
- **`DeadlockPreventionTest.java`**: Simulates concurrent opposite-order multi-seat booking requests (`[Seat 2, Seat 1]` vs `[Seat 1, Seat 2]`), verifying zero deadlocks via canonical ID sorting.
- **`PaymentFailureResilienceTest.java`**: Tests explicit payment failure, hold abandonment reconciliation, late payment refund trigger, and duplicate webhook idempotency.

### B. Containerized Chaos Automation (`run_chaos.ps1`)
Orchestrates live container kills and network disruptions against a running Docker Compose deployment:

```powershell
# Run all chaos scenarios
.\tests\chaos\run_chaos.ps1 -Scenario all

# Run specific chaos scenario
.\tests\chaos\run_chaos.ps1 -Scenario booking
.\tests\chaos\run_chaos.ps1 -Scenario redis
.\tests\chaos\run_chaos.ps1 -Scenario kafka
```

---

## 4. Post-Chaos Invariant Verification

After chaos injection, run the database invariant verification suite:

```powershell
psql -h localhost -U railscale -d railscale_booking -f tests/load/verify-invariants.sql
```

This verifies:
- `OVERSELLING_CHECK`: Count must be 0.
- `ORPHANED_HOLD_CHECK`: Count must be 0 (no seats stuck in `HELD` without a valid booking).
- `HOLD_METADATA_INTEGRITY`: Count must be 0 (`AVAILABLE` or `BOOKED` seats must have null `held_by` and `held_until`).
- `IDEMPOTENCY_INTEGRITY`: Count must be 0.

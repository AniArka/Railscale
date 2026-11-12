# RailScale — Kafka & Event-Driven Architecture
## Asynchronous Messaging, Partitioning & Outbox Delivery

---

## 1. Overview & Topics Hierarchy

RailScale leverages **Apache Kafka** as its distributed event backbone to decouple the synchronous checkout transaction path from downstream notifications, billing integrations, and analytics.

### Kafka Topics Matrix

| Topic Name | Producer(s) | Primary Consumer(s) | Partition Key | Purpose |
|---|---|---|---|---|
| `railscale.booking.events` | Booking Service (Outbox) | Notification Service, Payment Service | `bookingId` | Emits `BookingCreated`, `BookingConfirmed`, `BookingExpired`, `BookingCancelled` |
| `railscale.payment.events` | Payment Service | Booking Service, Notification Service | `bookingId` | Emits `PaymentInitiated`, `PaymentCompleted`, `PaymentFailed` |
| `railscale.inventory.events`| Booking Service (Outbox) | Search Service (Cache Sync), Analytics | `journeyId` | Emits `SeatHeld`, `SeatReleased`, `SeatBooked` |
| `railscale.notification.events`| System Services | Notification Service | `userId` | Emits user-bound email/SMS notification directives |

---

## 2. Standardized Event Envelope

All events published to Kafka wrap domain payloads in a standard JSON envelope:

```json
{
  "eventId": "b2f676b7-8ce2-4c28-98e9-4e78dc2db2f1",
  "eventType": "BookingConfirmed",
  "aggregateType": "Booking",
  "aggregateId": "7f8e9d0a-1b2c-3d4e-5f6a-7b8c9d0e1f2a",
  "timestamp": "2026-12-01T10:02:15.823Z",
  "version": 1,
  "payload": {
    "bookingId": "7f8e9d0a-1b2c-3d4e-5f6a-7b8c9d0e1f2a",
    "userId": "c2a3325e-9278-4a34-9c8b-21f07ced891f",
    "journeyId": "4a180f68-7c85-48b2-bdfa-bbf6e5349f2b",
    "amount": 2500.00,
    "confirmedAt": "2026-12-01T10:02:15.823Z"
  }
}
```

### Envelope Benefits:
- **`eventId`:** Globally unique UUID enables consumer deduplication via `processed_events`.
- **`aggregateId` as Partition Key:** Guarantees strict in-order processing per booking or journey.
- **Trace Context Propagation:** Includes OpenTelemetry `traceparent` headers for distributed tracing across Kafka boundaries.

---

## 3. The Transactional Outbox Publisher

Direct publishing from application threads risks dropped messages or dual-write anomalies. The `OutboxPublisher` runs as a high-frequency polling service.

```mermaid
flowchart TD
    DB[("PostgreSQL\noutbox_events")] -->|1. SELECT ... WHERE status='PENDING'\nAND (lease_until IS NULL OR lease_until < now)\nFOR UPDATE SKIP LOCKED LIMIT 100| Poller["OutboxPublisher Daemon"]
    Poller -->|2. Claim lease: lock_owner=workerId, lease_until=now+30s| DB
    Poller -->|3. Publish to Kafka Topic| Kafka[("Kafka Broker")]
    Kafka -->|4. Broker ACK| Poller
    Poller -->|5. UPDATE status='PUBLISHED', published_at=now| DB
```

### Worker Crash Recovery (Lease Reclaiming)
- If a worker crashes after claiming an outbox batch (step 2) but before publishing (step 3), its 30-second lease prevents duplicate publishing by peers while the worker is active.
- After 30 seconds, the lease expires (`lease_until < now`), allowing a healthy peer instance to automatically reclaim and publish the orphaned records without operator intervention.

---

## 4. Consumer Architecture & Idempotency

All consumers adhere to three strict principles:
1. **Manual ACKs (`AckMode.MANUAL_IMMEDIATE`):** Kafka offset is only committed after the local database transaction successfully commits.
2. **Idempotency Gate:** Checks and records `(consumer_group, event_id)` in PostgreSQL before executing side effects.
3. **Dead Letter Queue (DLQ):** Unparseable messages or permanent deserialization errors are routed to `<topic>.DLQ` after 3 retries with exponential backoff.

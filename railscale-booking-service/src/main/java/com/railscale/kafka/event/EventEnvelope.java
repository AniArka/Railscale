package com.railscale.kafka.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Standard event envelope for Kafka messages (Architecture Section 12).
 * Wraps outbox events into a consistent Kafka message format.
 *
 * Format:
 * {
 *   "eventId": "evt-123",
 *   "eventType": "BookingHeld",
 *   "aggregateId": "BKG-123",
 *   "aggregateType": "Booking",
 *   "timestamp": "2026-09-04T02:00:00Z",
 *   "payload": { ... }
 * }
 */
public class EventEnvelope {

    private UUID eventId;
    private String eventType;
    private UUID aggregateId;
    private String aggregateType;
    private Instant timestamp;
    private String payload;

    public EventEnvelope() {
    }

    public EventEnvelope(UUID eventId, String eventType, UUID aggregateId,
                         String aggregateType, Instant timestamp, String payload) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.aggregateType = aggregateType;
        this.timestamp = timestamp;
        this.payload = payload;
    }

    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(UUID aggregateId) {
        this.aggregateId = aggregateId;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public void setAggregateType(String aggregateType) {
        this.aggregateType = aggregateType;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }
}

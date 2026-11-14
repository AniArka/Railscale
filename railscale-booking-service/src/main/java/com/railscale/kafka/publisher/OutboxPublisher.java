package com.railscale.kafka.publisher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.railscale.kafka.config.KafkaTopics;
import com.railscale.kafka.event.EventEnvelope;
import com.railscale.outbox.domain.OutboxEvent;
import com.railscale.outbox.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Outbox Publisher Service (Architecture Sections 14-15).
 *
 * Implements the claim/lease mechanism:
 * 1. Poll: Find claimable outbox events (unpublished + unclaimed/expired)
 * 2. Claim: Atomically mark batch with claimedBy + claimedUntil -> COMMIT
 * 3. Publish: Send to Kafka (OUTSIDE DB transaction - Invariant 4)
 * 4. Mark: Set published_at -> COMMIT
 * 5. On failure: Record error, release claim
 *
 * At-least-once publication is acceptable because consumers are idempotent.
 */
@Service
@ConditionalOnBean(KafkaTemplate.class)
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final UUID publisherId = UUID.randomUUID();
    private final int batchSize;
    private final Duration leaseDuration;
    private com.railscale.metrics.BookingMetrics bookingMetrics;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setBookingMetrics(com.railscale.metrics.BookingMetrics bookingMetrics) {
        this.bookingMetrics = bookingMetrics;
    }

    public OutboxPublisher(
        OutboxEventRepository outboxEventRepository,
        KafkaTemplate<String, String> kafkaTemplate,
        ObjectMapper objectMapper,
        TransactionTemplate transactionTemplate,
        @Value("${railscale.outbox.batch-size:50}") int batchSize,
        @Value("${railscale.outbox.lease-duration-ms:30000}") long leaseDurationMs
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.batchSize = batchSize;
        this.leaseDuration = Duration.ofMillis(leaseDurationMs);
    }

    /**
     * Scheduled polling to publish outbox events to Kafka.
     * Runs at a fixed rate configured by railscale.outbox.poll-interval-ms.
     * Network calls to Kafka occur outside the DB transaction (Invariant 4 / Section 15).
     */
    @Scheduled(fixedDelayString = "${railscale.outbox.poll-interval-ms:1000}")
    public void pollAndPublish() {
        try {
            // 1. Release any expired claims from crashed publishers in an isolated transaction
            releaseExpiredClaims();

            // 2. Find and claim a batch of events in an isolated transaction
            List<OutboxEvent> claimedEvents = claimBatch();
            if (claimedEvents == null || claimedEvents.isEmpty()) {
                return;
            }

            log.debug("Claimed {} outbox events for publishing", claimedEvents.size());

            // 3. Publish each event to Kafka (OUTSIDE DB transaction per Section 15)
            for (OutboxEvent event : claimedEvents) {
                publishEvent(event);
            }
        } catch (Exception e) {
            log.error("Outbox publisher error: {}", e.getMessage(), e);
        }
    }

    /**
     * Step 1: Release expired claims so other publishers can reclaim.
     */
    public void releaseExpiredClaims() {
        transactionTemplate.executeWithoutResult(status -> {
            int released = outboxEventRepository.releaseExpiredClaims(Instant.now());
            if (released > 0) {
                log.info("Released {} expired outbox claims", released);
            }
        });
    }

    /**
     * Step 2: Find claimable events and atomically claim them.
     * Returns the list of claimed events.
     */
    public List<OutboxEvent> claimBatch() {
        return transactionTemplate.execute(status -> {
            Instant now = Instant.now();
            List<OutboxEvent> events = outboxEventRepository.findClaimableEvents(
                now, PageRequest.of(0, batchSize)
            );

            if (events == null || events.isEmpty()) {
                return List.of();
            }

            List<UUID> eventIds = events.stream().map(OutboxEvent::getId).toList();
            Instant claimedUntil = now.plus(leaseDuration);

            outboxEventRepository.claimBatch(eventIds, publisherId, claimedUntil);

            return events;
        });
    }

    /**
     * Step 3 & 4: Publish a single event to Kafka and mark as published.
     * If publishing fails, record the error and release the claim.
     */
    private void publishEvent(OutboxEvent event) {
        String topic = resolveTopicForEvent(event);
        String partitionKey = event.getAggregateId().toString();
        String message = buildEnvelopeJson(event);

        try {
            // Synchronous send to ensure we know the result before marking published
            kafkaTemplate.send(topic, partitionKey, message).get();

            // Mark as published in a separate transaction
            markPublished(event.getId());

            log.debug("Published outbox event {} ({}) to topic {}",
                event.getId(), event.getEventType(), topic);
        } catch (Exception e) {
            log.error("Failed to publish outbox event {} to Kafka: {}",
                event.getId(), e.getMessage());
            recordFailure(event.getId(), e.getMessage());
        }
    }

    public void markPublished(UUID eventId) {
        transactionTemplate.executeWithoutResult(status ->
            outboxEventRepository.markPublished(eventId, Instant.now())
        );
    }

    public void recordFailure(UUID eventId, String error) {
        if (bookingMetrics != null) {
            bookingMetrics.incrementKafkaPublishFailures();
        }
        String truncatedError = error != null && error.length() > 500
            ? error.substring(0, 500) : error;
        transactionTemplate.executeWithoutResult(status ->
            outboxEventRepository.recordFailure(eventId, truncatedError)
        );
    }

    /**
     * Route outbox events to the correct Kafka topic based on aggregateType and eventType.
     * Per Architecture Section 12.
     */
    private String resolveTopicForEvent(OutboxEvent event) {
        return switch (event.getEventType()) {
            case "BookingHeld", "BookingConfirmed", "BookingCancelled" -> KafkaTopics.BOOKING_EVENTS;
            case "PaymentInitiated", "PaymentCompleted", "PaymentFailed" -> KafkaTopics.PAYMENT_EVENTS;
            case "SeatReleased" -> KafkaTopics.INVENTORY_EVENTS;
            case "RefundRequired" -> KafkaTopics.REFUND_EVENTS;
            default -> {
                log.warn("Unknown event type: {}, routing to booking.events", event.getEventType());
                yield KafkaTopics.BOOKING_EVENTS;
            }
        };
    }

    /**
     * Build the EventEnvelope JSON wrapping the outbox event.
     */
    private String buildEnvelopeJson(OutboxEvent event) {
        EventEnvelope envelope = new EventEnvelope(
            event.getId(),
            event.getEventType(),
            event.getAggregateId(),
            event.getAggregateType(),
            event.getCreatedAt(),
            event.getPayload()
        );

        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event envelope for event {}", event.getId(), e);
            // Fall back to raw payload
            return event.getPayload();
        }
    }
}

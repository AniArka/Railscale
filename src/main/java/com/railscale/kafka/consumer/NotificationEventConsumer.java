package com.railscale.kafka.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.railscale.event.domain.ProcessedEvent;
import com.railscale.event.domain.ProcessedEventId;
import com.railscale.event.repository.ProcessedEventRepository;
import com.railscale.kafka.config.KafkaTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Kafka consumer for notification events (Architecture Section 32).
 * Simulates email/SMS/ticket notifications for booking lifecycle events.
 *
 * Idempotency: Uses processed_events table (Invariant 7).
 * Consumes from booking.events and inventory.events topics.
 */
@Service
@ConditionalOnBean(KafkaTemplate.class)
public class NotificationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventConsumer.class);
    private static final String CONSUMER_NAME = "notification-consumer";

    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    public NotificationEventConsumer(
        ProcessedEventRepository processedEventRepository,
        ObjectMapper objectMapper
    ) {
        this.processedEventRepository = processedEventRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
        topics = {KafkaTopics.BOOKING_EVENTS, KafkaTopics.INVENTORY_EVENTS},
        groupId = "railscale-notification-consumer",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleNotificationEvent(String message, Acknowledgment ack) {
        try {
            JsonNode envelope = objectMapper.readTree(message);
            String eventType = envelope.path("eventType").asText();
            UUID eventId = UUID.fromString(envelope.path("eventId").asText());

            // Idempotency check (Invariant 7)
            ProcessedEventId peId = new ProcessedEventId(CONSUMER_NAME, eventId);
            if (processedEventRepository.existsById(peId)) {
                log.debug("Duplicate notification event ignored: {}", eventId);
                ack.acknowledge();
                return;
            }

            try {
                processedEventRepository.saveAndFlush(
                    new ProcessedEvent(CONSUMER_NAME, eventId, Instant.now())
                );
            } catch (DataIntegrityViolationException e) {
                log.debug("Duplicate notification event (race): {}", eventId);
                ack.acknowledge();
                return;
            }

            JsonNode payload = envelope.path("payload");
            if (payload.isTextual()) {
                payload = objectMapper.readTree(payload.asText());
            }

            // Simulate notification dispatch
            switch (eventType) {
                case "BookingHeld" -> {
                    UUID bookingId = UUID.fromString(payload.path("bookingId").asText());
                    log.info("[NOTIFICATION] Booking {} held — sending hold confirmation email", bookingId);
                }
                case "BookingConfirmed" -> {
                    UUID bookingId = UUID.fromString(payload.path("bookingId").asText());
                    log.info("[NOTIFICATION] Booking {} confirmed — sending e-ticket via email and SMS", bookingId);
                }
                case "BookingCancelled" -> {
                    UUID bookingId = UUID.fromString(payload.path("bookingId").asText());
                    log.info("[NOTIFICATION] Booking {} cancelled — sending cancellation notice", bookingId);
                }
                case "SeatReleased" -> {
                    UUID bookingId = UUID.fromString(payload.path("bookingId").asText());
                    log.info("[NOTIFICATION] Seats released for booking {} — availability updated", bookingId);
                }
                default -> log.debug("Notification consumer ignoring event type: {}", eventType);
            }

            ack.acknowledge();

        } catch (JsonProcessingException e) {
            log.error("Failed to parse notification event: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Error processing notification event: {}", e.getMessage(), e);
        }
    }
}

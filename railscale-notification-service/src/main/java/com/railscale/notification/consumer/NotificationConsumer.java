package com.railscale.notification.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.railscale.common.event.KafkaTopics;
import com.railscale.notification.domain.ProcessedEvent;
import com.railscale.notification.domain.ProcessedEventId;
import com.railscale.notification.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Notification Service Kafka Consumer (Architecture Section 32).
 * Simulates email/SMS/ticket dispatch with idempotency via processed_events table (Invariant 7).
 */
@Service
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);
    private static final String CONSUMER_NAME = "notification-service";

    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    public NotificationConsumer(
        ProcessedEventRepository processedEventRepository,
        ObjectMapper objectMapper
    ) {
        this.processedEventRepository = processedEventRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
        topics = {KafkaTopics.BOOKING_EVENTS, KafkaTopics.INVENTORY_EVENTS, KafkaTopics.REFUND_EVENTS},
        groupId = "railscale-notification-service"
    )
    @Transactional
    public void handleNotificationEvent(String message) {
        try {
            JsonNode envelope = objectMapper.readTree(message);
            String eventType = envelope.path("eventType").asText();
            UUID eventId = UUID.fromString(envelope.path("eventId").asText());

            // Idempotency check (Invariant 7)
            ProcessedEventId peId = new ProcessedEventId(CONSUMER_NAME, eventId);
            if (processedEventRepository.existsById(peId)) {
                log.debug("Duplicate notification event ignored: {}", eventId);
                return;
            }

            try {
                processedEventRepository.saveAndFlush(
                    new ProcessedEvent(CONSUMER_NAME, eventId, Instant.now())
                );
            } catch (DataIntegrityViolationException e) {
                log.debug("Duplicate notification event (race condition) ignored: {}", eventId);
                return;
            }

            JsonNode payload = envelope.path("payload");
            if (payload.isTextual()) {
                payload = objectMapper.readTree(payload.asText());
            }

            // Dispatch simulated notifications
            switch (eventType) {
                case "BookingHeld" -> {
                    String bookingId = payload.path("bookingId").asText();
                    log.info("[EMAIL/SMS] Booking {} held. Temporary hold expires soon.", bookingId);
                }
                case "BookingConfirmed" -> {
                    String bookingId = payload.path("bookingId").asText();
                    log.info("[E-TICKET] Booking {} CONFIRMED! Sending ticket to customer.", bookingId);
                }
                case "BookingCancelled" -> {
                    String bookingId = payload.path("bookingId").asText();
                    log.info("[EMAIL] Booking {} CANCELLED.", bookingId);
                }
                case "SeatReleased" -> {
                    String seatId = payload.path("seatId").asText();
                    log.info("[INVENTORY] Seat {} released back to inventory.", seatId);
                }
                case "RefundRequired" -> {
                    String bookingId = payload.path("bookingId").asText();
                    String amount = payload.path("amount").asText();
                    log.info("[REFUND NOTIFICATION] Refund of {} queued for booking {}.", amount, bookingId);
                }
                default -> log.debug("Notification service ignoring event type: {}", eventType);
            }

        } catch (JsonProcessingException e) {
            log.error("Failed to parse notification event: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Error processing notification event: {}", e.getMessage(), e);
        }
    }
}

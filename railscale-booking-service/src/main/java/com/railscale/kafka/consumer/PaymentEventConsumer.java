package com.railscale.kafka.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.railscale.booking.service.BookingService;
import com.railscale.kafka.config.KafkaTopics;
import com.railscale.payment.domain.PaymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Kafka consumer for payment events (Architecture Section 13).
 * Listens on payment.events topic for PaymentCompleted and PaymentFailed.
 *
 * Idempotency: Uses processed_events table via BookingService.processPaymentResult().
 * Manual ACK: Only acknowledges after successful DB transaction commit.
 * At-least-once: Kafka redelivers if ACK not received.
 */
@Service
@ConditionalOnBean(KafkaTemplate.class)
public class PaymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);

    private final BookingService bookingService;
    private final ObjectMapper objectMapper;

    public PaymentEventConsumer(BookingService bookingService, ObjectMapper objectMapper) {
        this.bookingService = bookingService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
        topics = KafkaTopics.PAYMENT_EVENTS,
        groupId = "railscale-payment-consumer",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handlePaymentEvent(String message, Acknowledgment ack) {
        try {
            JsonNode envelope = objectMapper.readTree(message);
            String eventType = envelope.path("eventType").asText();
            UUID eventId = UUID.fromString(envelope.path("eventId").asText());

            JsonNode payload = envelope.path("payload");
            if (payload.isTextual()) {
                payload = objectMapper.readTree(payload.asText());
            }

            UUID bookingId = UUID.fromString(payload.path("bookingId").asText());

            log.info("Received payment event: type={}, eventId={}, bookingId={}",
                eventType, eventId, bookingId);

            switch (eventType) {
                case "PaymentCompleted" -> {
                    bookingService.processPaymentResult(bookingId, PaymentStatus.SUCCESS, eventId);
                    log.info("Processed PaymentCompleted for booking {}", bookingId);
                }
                case "PaymentFailed" -> {
                    bookingService.processPaymentResult(bookingId, PaymentStatus.FAILED, eventId);
                    log.info("Processed PaymentFailed for booking {}", bookingId);
                }
                case "PaymentInitiated" -> {
                    log.info("PaymentInitiated event received for booking {} — acknowledged", bookingId);
                }
                default -> log.warn("Unknown payment event type: {}", eventType);
            }

            // ACK after successful DB commit (Section 13)
            ack.acknowledge();

        } catch (JsonProcessingException e) {
            log.error("Failed to parse payment event: {}", e.getMessage());
            // Don't ACK — message will be redelivered
        } catch (Exception e) {
            log.error("Error processing payment event: {}", e.getMessage(), e);
            // Don't ACK — message will be redelivered
        }
    }
}

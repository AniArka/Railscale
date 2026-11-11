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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Kafka consumer for refund events (Architecture Section 10-11, Invariant 9).
 * Handles RefundRequired events by simulating refund processing.
 *
 * In a real system, this would integrate with a payment gateway.
 * For RailScale v1, it logs the refund and marks it as processed.
 *
 * Idempotency: Uses processed_events table (Invariant 7).
 */
@Service
@ConditionalOnBean(KafkaTemplate.class)
public class RefundEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(RefundEventConsumer.class);
    private static final String CONSUMER_NAME = "refund-consumer";

    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    public RefundEventConsumer(
        ProcessedEventRepository processedEventRepository,
        ObjectMapper objectMapper
    ) {
        this.processedEventRepository = processedEventRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
        topics = KafkaTopics.REFUND_EVENTS,
        groupId = "railscale-refund-consumer",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleRefundEvent(String message, Acknowledgment ack) {
        try {
            JsonNode envelope = objectMapper.readTree(message);
            String eventType = envelope.path("eventType").asText();
            UUID eventId = UUID.fromString(envelope.path("eventId").asText());

            // Idempotency check (Invariant 7)
            ProcessedEventId peId = new ProcessedEventId(CONSUMER_NAME, eventId);
            if (processedEventRepository.existsById(peId)) {
                log.debug("Duplicate refund event ignored: {}", eventId);
                ack.acknowledge();
                return;
            }

            try {
                processedEventRepository.saveAndFlush(
                    new ProcessedEvent(CONSUMER_NAME, eventId, Instant.now())
                );
            } catch (DataIntegrityViolationException e) {
                log.debug("Duplicate refund event (race): {}", eventId);
                ack.acknowledge();
                return;
            }

            if ("RefundRequired".equals(eventType)) {
                JsonNode payload = envelope.path("payload");
                if (payload.isTextual()) {
                    payload = objectMapper.readTree(payload.asText());
                }

                UUID bookingId = UUID.fromString(payload.path("bookingId").asText());
                String amount = payload.path("amount").asText("0.00");
                String reason = payload.path("reason").asText("UNKNOWN");

                log.info("[REFUND] Processing refund for booking {}: amount={}, reason={}",
                    bookingId, amount, reason);

                // In a real system, this would call a payment gateway API
                // For simulation, we just log and acknowledge
                log.info("[REFUND] Refund of {} initiated for booking {} (reason: {})",
                    amount, bookingId, reason);
            } else {
                log.warn("Unknown refund event type: {}", eventType);
            }

            ack.acknowledge();

        } catch (JsonProcessingException e) {
            log.error("Failed to parse refund event: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Error processing refund event: {}", e.getMessage(), e);
        }
    }
}

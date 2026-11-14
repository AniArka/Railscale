package com.railscale.search.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.railscale.common.event.KafkaTopics;
import com.railscale.search.domain.JourneySearchProjection;
import com.railscale.search.repository.JourneySearchProjectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Consumes Kafka events to update the journey search projection asynchronously.
 * Implements eventual consistency per Architecture Section 28.
 */
@Service
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class SearchProjectionConsumer {

    private static final Logger log = LoggerFactory.getLogger(SearchProjectionConsumer.class);

    private final JourneySearchProjectionRepository projectionRepository;
    private final ObjectMapper objectMapper;

    public SearchProjectionConsumer(
        JourneySearchProjectionRepository projectionRepository,
        ObjectMapper objectMapper
    ) {
        this.projectionRepository = projectionRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
        topics = {KafkaTopics.INVENTORY_EVENTS, KafkaTopics.BOOKING_EVENTS},
        groupId = "railscale-search-projection"
    )
    @Transactional
    public void handleEvent(String message) {
        try {
            JsonNode envelope = objectMapper.readTree(message);
            String eventType = envelope.path("eventType").asText();

            JsonNode payload = envelope.path("payload");
            if (payload.isTextual()) {
                payload = objectMapper.readTree(payload.asText());
            }

            if (!payload.has("journeyId")) {
                return;
            }

            UUID journeyId = UUID.fromString(payload.path("journeyId").asText());
            Optional<JourneySearchProjection> optional = projectionRepository.findById(journeyId);
            if (optional.isEmpty()) {
                return;
            }

            JourneySearchProjection projection = optional.get();
            int currentCount = projection.getAvailableSeatCount();

            switch (eventType) {
                case "BookingHeld" -> {
                    int seatCount = payload.has("seatCount") ? payload.path("seatCount").asInt(1) : 1;
                    projection.setAvailableSeatCount(Math.max(0, currentCount - seatCount));
                    projection.setLastUpdated(Instant.now());
                    projectionRepository.save(projection);
                    log.debug("Search projection updated: Journey {} decremented by {}", journeyId, seatCount);
                }
                case "BookingCancelled", "SeatReleased" -> {
                    int seatCount = payload.has("seatCount") ? payload.path("seatCount").asInt(1) : 1;
                    projection.setAvailableSeatCount(currentCount + seatCount);
                    projection.setLastUpdated(Instant.now());
                    projectionRepository.save(projection);
                    log.debug("Search projection updated: Journey {} incremented by {}", journeyId, seatCount);
                }
                default -> log.trace("Search consumer ignoring event type: {}", eventType);
            }

        } catch (Exception e) {
            log.error("Failed to process event for search projection: {}", e.getMessage(), e);
        }
    }
}

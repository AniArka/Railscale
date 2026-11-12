package com.railscale;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.railscale.kafka.publisher.OutboxPublisher;
import com.railscale.outbox.domain.OutboxEvent;
import com.railscale.outbox.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Phase 9 Chaos Test: Section 12 — Outbox Publisher Crash and Lease Expiration Recovery.
 *
 * Simulates:
 * 1. Publisher A claims an outbox event with a 30s lease.
 * 2. Publisher A crashes (dies) before sending to Kafka.
 * 3. Lease expires (claimed_until passes).
 * 4. Publisher B starts up, detects expired claim, reclaims event, and publishes to Kafka.
 * 5. Event is marked published in the database.
 */
@SpringBootTest
@ActiveProfiles("test")
class OutboxPublisherCrashRecoveryTest {

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @BeforeEach
    void cleanUp() {
        outboxEventRepository.deleteAll();

        // Mock Kafka send to always succeed
        SendResult<String, String> mockSendResult = Mockito.mock(SendResult.class);
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(mockSendResult);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);
    }

    @Test
    @DisplayName("Chaos Test: Crashed publisher lease expiry -> Surviving publisher reclaims and publishes event")
    void testCrashedPublisherLeaseExpiryAndReclaim() {
        UUID aggregateId = UUID.randomUUID();
        UUID crashedPublisherId = UUID.randomUUID();

        // 1. Create Outbox Event
        OutboxEvent event = new OutboxEvent(
                "Booking",
                aggregateId,
                "BookingHeld",
                "{\"bookingId\":\"" + aggregateId + "\"}"
        );
        event = outboxEventRepository.save(event);

        // 2. Simulate: Publisher A claimed the event 1 minute ago, then crashed.
        // Its lease has now expired (claimed_until is 30 seconds in the past).
        Instant expiredClaimTime = Instant.now().minus(30, ChronoUnit.SECONDS);
        event.setClaimedBy(crashedPublisherId);
        event.setClaimedUntil(expiredClaimTime);
        outboxEventRepository.save(event);

        // Verify state prior to recovery: claimed by crashed publisher, but expired and unpublished
        OutboxEvent preState = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(preState.getClaimedBy()).isEqualTo(crashedPublisherId);
        assertThat(preState.getClaimedUntil()).isBefore(Instant.now());
        assertThat(preState.getPublishedAt()).isNull();

        // 3. Publisher B (new surviving instance) starts up
        OutboxPublisher publisherB = new OutboxPublisher(
                outboxEventRepository,
                kafkaTemplate,
                objectMapper,
                transactionTemplate,
                50,
                30000L
        );

        // 4. Publisher B executes pollAndPublish()
        publisherB.pollAndPublish();

        // 5. Verification:
        // The event was reclaimed, published to Kafka, and marked published_at!
        OutboxEvent postState = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(postState.getPublishedAt()).isNotNull();
        assertThat(postState.getClaimedBy()).isNotEqualTo(crashedPublisherId);
    }

    @Test
    @DisplayName("Chaos Test: Active unexpired lease is NOT stolen by another publisher")
    void testActiveUnexpiredLeaseIsNotStolen() {
        UUID aggregateId = UUID.randomUUID();
        UUID activePublisherId = UUID.randomUUID();

        // Event currently claimed with a valid lease until 2 minutes into future
        OutboxEvent event = new OutboxEvent(
                "Booking",
                aggregateId,
                "BookingHeld",
                "{\"bookingId\":\"" + aggregateId + "\"}"
        );
        event.setClaimedBy(activePublisherId);
        event.setClaimedUntil(Instant.now().plus(2, ChronoUnit.MINUTES));
        event = outboxEventRepository.save(event);

        // Competitor publisher starts
        OutboxPublisher competitorPublisher = new OutboxPublisher(
                outboxEventRepository,
                kafkaTemplate,
                objectMapper,
                transactionTemplate,
                50,
                30000L
        );

        // Competitor poll must NOT steal or claim this active event
        competitorPublisher.pollAndPublish();

        OutboxEvent state = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(state.getClaimedBy()).isEqualTo(activePublisherId);
        assertThat(state.getPublishedAt()).isNull();
    }
}

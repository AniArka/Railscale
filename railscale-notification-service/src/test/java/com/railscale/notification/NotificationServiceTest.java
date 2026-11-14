package com.railscale.notification;

import com.railscale.notification.domain.ProcessedEvent;
import com.railscale.notification.domain.ProcessedEventId;
import com.railscale.notification.repository.ProcessedEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = NotificationServiceApplication.class)
@ActiveProfiles("test")
@Transactional
class NotificationServiceTest {

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Test
    @DisplayName("Should track processed events for notification idempotency")
    void testProcessedEventTracking() {
        UUID eventId = UUID.randomUUID();
        String consumerName = "notification-service";

        ProcessedEventId id = new ProcessedEventId(consumerName, eventId);
        assertThat(processedEventRepository.existsById(id)).isFalse();

        processedEventRepository.save(new ProcessedEvent(consumerName, eventId, Instant.now()));

        assertThat(processedEventRepository.existsById(id)).isTrue();
    }
}

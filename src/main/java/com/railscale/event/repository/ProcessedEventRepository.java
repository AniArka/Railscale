package com.railscale.event.repository;

import com.railscale.event.domain.ProcessedEvent;
import com.railscale.event.domain.ProcessedEventId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, ProcessedEventId> {

    /**
     * Section 12 & 25: Atomic insertion of processed event record.
     * Returns 1 if event was not yet recorded, 0 if duplicate.
     */
    @Modifying
    @Query(value = """
        INSERT INTO processed_events (consumer_name, event_id, processed_at)
        VALUES (:consumerName, :eventId, :processedAt)
        ON CONFLICT (consumer_name, event_id) DO NOTHING
    """, nativeQuery = true)
    int insertIfAbsent(
        @Param("consumerName") String consumerName,
        @Param("eventId") UUID eventId,
        @Param("processedAt") Instant processedAt
    );
}

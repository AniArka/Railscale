package com.railscale.outbox.repository;

import com.railscale.outbox.domain.OutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Section 14 & 15: Find unpublished events that are either unclaimed or whose lease expired.
     */
    @Query("""
        SELECT e FROM OutboxEvent e
        WHERE e.publishedAt IS NULL
          AND (e.claimedUntil IS NULL OR e.claimedUntil < :now)
        ORDER BY e.createdAt ASC
    """)
    List<OutboxEvent> findClaimableEvents(@Param("now") Instant now, Pageable pageable);

    @Modifying
    @Query("""
        UPDATE OutboxEvent e
        SET e.claimedBy = :claimedBy, e.claimedUntil = :claimedUntil, e.attemptCount = e.attemptCount + 1
        WHERE e.id IN :ids
    """)
    int claimBatch(
        @Param("ids") Collection<UUID> ids,
        @Param("claimedBy") UUID claimedBy,
        @Param("claimedUntil") Instant claimedUntil
    );

    @Modifying
    @Query("""
        UPDATE OutboxEvent e
        SET e.publishedAt = :publishedAt, e.claimedBy = NULL, e.claimedUntil = NULL
        WHERE e.id = :id
    """)
    int markPublished(@Param("id") UUID id, @Param("publishedAt") Instant publishedAt);

    @Modifying
    @Query("""
        UPDATE OutboxEvent e
        SET e.lastError = :error, e.claimedBy = NULL, e.claimedUntil = NULL
        WHERE e.id = :id
    """)
    int recordFailure(@Param("id") UUID id, @Param("error") String error);

    @Modifying
    @Query("""
        UPDATE OutboxEvent e
        SET e.claimedBy = NULL, e.claimedUntil = NULL
        WHERE e.publishedAt IS NULL AND e.claimedUntil < :now
    """)
    int releaseExpiredClaims(@Param("now") Instant now);
}

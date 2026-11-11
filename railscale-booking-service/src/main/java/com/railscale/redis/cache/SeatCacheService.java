package com.railscale.redis.cache;

import com.railscale.inventory.domain.SeatStatus;
import com.railscale.redis.config.RedisHealthIndicator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Caches seat availability per journey in Redis to reduce PostgreSQL reads.
 * Per Architecture Section 16:
 * - Key pattern: journey:{journeyId}:seats (Redis Hash)
 * - Each hash field is a seat inventory ID, value is the seat status
 * - TTL is configurable (default 30s) for eventual consistency
 * - Cache is invalidated/updated on every hold/release/confirm/cancel
 *
 * Per Invariant 1: PostgreSQL remains authoritative. Cache misses
 * fall through to the database. Redis unavailability is non-fatal.
 */
@ConditionalOnBean(org.springframework.data.redis.connection.RedisConnectionFactory.class)
@Service
public class SeatCacheService {

    private static final Logger log = LoggerFactory.getLogger(SeatCacheService.class);

    private static final String JOURNEY_SEATS_PREFIX = "journey:";
    private static final String SEATS_SUFFIX = ":seats";

    private final StringRedisTemplate redisTemplate;
    private final RedisHealthIndicator redisHealth;
    private final Duration cacheTtl;

    public SeatCacheService(
        StringRedisTemplate redisTemplate,
        RedisHealthIndicator redisHealth,
        @Value("${railscale.redis.seat-cache-ttl:30}") long cacheTtlSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.redisHealth = redisHealth;
        this.cacheTtl = Duration.ofSeconds(cacheTtlSeconds);
    }

    /**
     * Get cached seat statuses for a journey.
     * Returns empty map on cache miss or Redis unavailability.
     */
    public Map<UUID, SeatStatus> getCachedSeatStatuses(UUID journeyId) {
        if (!redisHealth.isAvailable()) {
            return Collections.emptyMap();
        }

        try {
            String key = buildKey(journeyId);
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);

            if (entries.isEmpty()) {
                return Collections.emptyMap();
            }

            Map<UUID, SeatStatus> result = new HashMap<>();
            for (Map.Entry<Object, Object> entry : entries.entrySet()) {
                try {
                    UUID seatId = UUID.fromString(entry.getKey().toString());
                    SeatStatus status = SeatStatus.valueOf(entry.getValue().toString());
                    result.put(seatId, status);
                } catch (IllegalArgumentException e) {
                    log.debug("Invalid cache entry for journey {}: {}", journeyId, entry);
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to read seat cache for journey {}: {}", journeyId, e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Update the cached status for a specific seat in a journey.
     */
    public void updateSeatStatus(UUID journeyId, UUID inventoryId, SeatStatus status) {
        if (!redisHealth.isAvailable()) {
            return;
        }

        try {
            String key = buildKey(journeyId);
            redisTemplate.opsForHash().put(key, inventoryId.toString(), status.name());
            redisTemplate.expire(key, cacheTtl);
            log.debug("Seat cache updated: journey={}, seat={}, status={}", journeyId, inventoryId, status);
        } catch (Exception e) {
            log.warn("Failed to update seat cache: {}", e.getMessage());
        }
    }

    /**
     * Bulk update seat statuses for a journey (used after hold/release operations).
     */
    public void bulkUpdateSeatStatuses(UUID journeyId, Map<UUID, SeatStatus> statuses) {
        if (!redisHealth.isAvailable() || statuses.isEmpty()) {
            return;
        }

        try {
            String key = buildKey(journeyId);
            Map<String, String> hashEntries = new HashMap<>();
            for (Map.Entry<UUID, SeatStatus> entry : statuses.entrySet()) {
                hashEntries.put(entry.getKey().toString(), entry.getValue().name());
            }
            redisTemplate.opsForHash().putAll(key, hashEntries);
            redisTemplate.expire(key, cacheTtl);
            log.debug("Seat cache bulk updated: journey={}, {} seats", journeyId, statuses.size());
        } catch (Exception e) {
            log.warn("Failed to bulk update seat cache: {}", e.getMessage());
        }
    }

    /**
     * Invalidate the entire journey seat cache.
     */
    public void invalidateJourneyCache(UUID journeyId) {
        if (!redisHealth.isAvailable()) {
            return;
        }

        try {
            String key = buildKey(journeyId);
            redisTemplate.delete(key);
            log.debug("Seat cache invalidated for journey: {}", journeyId);
        } catch (Exception e) {
            log.warn("Failed to invalidate seat cache: {}", e.getMessage());
        }
    }

    private String buildKey(UUID journeyId) {
        return JOURNEY_SEATS_PREFIX + journeyId + SEATS_SUFFIX;
    }
}

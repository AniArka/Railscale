package com.railscale.redis.lock;

import com.railscale.redis.config.RedisHealthIndicator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Distributed lock service using Redis for seat reservation coordination.
 * Per Architecture Section 16-18:
 * - Uses SET NX PX for atomic lock acquisition
 * - Uses owner-checked Lua script for safe release (never blind DEL)
 * - Multi-seat locks acquired in canonical inventory ID order to prevent deadlocks
 * - Partial lock failures trigger explicit cleanup of previously acquired locks
 *
 * Per Invariant 5: If Redis is unavailable, returns empty Optional and
 * the caller falls back to DB-only locking. Correctness is preserved.
 */
@ConditionalOnBean(org.springframework.data.redis.connection.RedisConnectionFactory.class)
@Service
public class RedisLockService {

    private static final Logger log = LoggerFactory.getLogger(RedisLockService.class);

    private static final String LOCK_KEY_PREFIX = "lock:";

    /**
     * Lua script for safe lock release (Section 18).
     * Only deletes the key if the current value matches the owner token.
     * Prevents: Instance A's expired lock being deleted after Instance B acquired it.
     */
    private static final String RELEASE_LOCK_SCRIPT =
        "if redis.call('GET', KEYS[1]) == ARGV[1] then " +
        "  return redis.call('DEL', KEYS[1]) " +
        "end " +
        "return 0";

    private final StringRedisTemplate redisTemplate;
    private final RedisHealthIndicator redisHealth;
    private final Duration lockTtl;

    /**
     * Constructs a new RedisLockService.
     *
     * @param redisTemplate the StringRedisTemplate for Redis operations
     * @param redisHealth   the health indicator checking Redis availability
     * @param lockTtlMs     the lock time-to-live in milliseconds
     */
    public RedisLockService(
        StringRedisTemplate redisTemplate,
        RedisHealthIndicator redisHealth,
        @Value("${railscale.redis.lock-ttl:5000}") long lockTtlMs
    ) {
        this.redisTemplate = redisTemplate;
        this.redisHealth = redisHealth;
        this.lockTtl = Duration.ofMillis(lockTtlMs);
    }

    /**
     * Acquire locks for multiple seats in canonical inventory ID order (Section 17).
     * Returns the owner token if ALL locks were acquired, or empty if any failed.
     * On partial failure, all previously acquired locks are explicitly released.
     *
     * @param journeyId    the journey being booked
     * @param inventoryIds the seat inventory IDs to lock
     * @return owner token if all locks acquired, empty if Redis unavailable or any lock failed
     */
    public Optional<String> acquireLocks(UUID journeyId, List<UUID> inventoryIds) {
        if (!redisHealth.isAvailable()) {
            log.info("Redis unavailable — skipping distributed lock, falling back to DB-only");
            return Optional.empty();
        }

        String ownerToken = UUID.randomUUID().toString();

        // Section 17: Sort by canonical inventory ID order to prevent deadlocks
        List<UUID> sortedIds = new ArrayList<>(inventoryIds);
        Collections.sort(sortedIds);

        List<String> acquiredKeys = new ArrayList<>();

        try {
            for (UUID inventoryId : sortedIds) {
                String key = buildLockKey(journeyId, inventoryId);
                Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(key, ownerToken, lockTtl);

                if (Boolean.TRUE.equals(acquired)) {
                    acquiredKeys.add(key);
                    log.debug("Lock acquired: {} by {}", key, ownerToken);
                } else {
                    // Section 17: Partial failure — release all previously acquired locks
                    log.debug("Lock acquisition failed for {}. Releasing {} previously acquired locks.",
                        key, acquiredKeys.size());
                    releaseAcquiredLocks(acquiredKeys, ownerToken);
                    return Optional.empty();
                }
            }

            return Optional.of(ownerToken);
        } catch (Exception e) {
            log.warn("Redis error during lock acquisition, releasing acquired locks: {}", e.getMessage());
            releaseAcquiredLocks(acquiredKeys, ownerToken);
            return Optional.empty();
        }
    }

    /**
     * Release locks for the given seats owned by the specified token (Section 18).
     * Uses a Lua script to ensure only the owner can release the lock.
     *
     * @param journeyId    the journey
     * @param inventoryIds the seat inventory IDs
     * @param ownerToken   the token that acquired the locks
     */
    public void releaseLocks(UUID journeyId, List<UUID> inventoryIds, String ownerToken) {
        if (ownerToken == null) {
            return;
        }

        try {
            for (UUID inventoryId : inventoryIds) {
                String key = buildLockKey(journeyId, inventoryId);
                releaseSingleLock(key, ownerToken);
            }
        } catch (Exception e) {
            log.warn("Error releasing Redis locks (will expire via TTL): {}", e.getMessage());
        }
    }

    private void releaseAcquiredLocks(List<String> keys, String ownerToken) {
        for (String key : keys) {
            try {
                releaseSingleLock(key, ownerToken);
            } catch (Exception e) {
                log.warn("Failed to release lock {} (will expire via TTL): {}", key, e.getMessage());
            }
        }
    }

    private void releaseSingleLock(String key, String ownerToken) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(RELEASE_LOCK_SCRIPT, Long.class);
        Long result = redisTemplate.execute(script, List.of(key), ownerToken);
        if (result != null && result == 1) {
            log.debug("Lock released: {} by {}", key, ownerToken);
        } else {
            log.debug("Lock not released (already expired or different owner): {}", key);
        }
    }

    private String buildLockKey(UUID journeyId, UUID inventoryId) {
        return LOCK_KEY_PREFIX + journeyId + ":" + inventoryId;
    }
}

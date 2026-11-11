package com.railscale.redis.ratelimit;

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
import java.util.UUID;

/**
 * Sliding window rate limiter using Redis (Architecture Section 40).
 * Limits booking attempts per user per configurable time window.
 * If Redis is unavailable, rate limiting is bypassed (fail-open).
 */
@ConditionalOnBean(org.springframework.data.redis.connection.RedisConnectionFactory.class)
@Service
public class RedisRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);

    private static final String RATE_KEY_PREFIX = "rate:";

    private final StringRedisTemplate redisTemplate;
    private final RedisHealthIndicator redisHealth;
    private final int maxRequests;
    private final Duration windowDuration;

    /**
     * Constructs a new RedisRateLimiter.
     *
     * @param redisTemplate the StringRedisTemplate for Redis operations
     * @param redisHealth the RedisHealthIndicator to check availability
     * @param maxRequests the maximum allowed requests in the time window
     * @param windowSeconds the time window duration in seconds
     */
    public RedisRateLimiter(
        StringRedisTemplate redisTemplate,
        RedisHealthIndicator redisHealth,
        @Value("${railscale.redis.rate-limit.max-requests:100}") int maxRequests,
        @Value("${railscale.redis.rate-limit.window-seconds:60}") long windowSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.redisHealth = redisHealth;
        this.maxRequests = maxRequests;
        this.windowDuration = Duration.ofSeconds(windowSeconds);
    }

    /**
     * Check if the user is within rate limits.
     * Returns true if the request is allowed, false if rate limited.
     * If Redis is unavailable, returns true (fail-open — don't block users).
     *
     * @param userId the user making the request
     * @return true if allowed, false if rate limited
     */
    public boolean isAllowed(UUID userId) {
        if (!redisHealth.isAvailable()) {
            return true;
        }

        try {
            long currentWindow = System.currentTimeMillis() / (windowDuration.toMillis());
            String key = RATE_KEY_PREFIX + userId + ":" + currentWindow;

            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                // First request in this window — set expiry
                redisTemplate.expire(key, windowDuration.plusSeconds(5));
            }

            if (count != null && count > maxRequests) {
                log.warn("Rate limit exceeded for user {}: {}/{} in window",
                    userId, count, maxRequests);
                return false;
            }

            return true;
        } catch (Exception e) {
            log.warn("Rate limit check failed, allowing request: {}", e.getMessage());
            return true;
        }
    }

    /**
     * Get the remaining requests for a user in the current window.
     *
     * @param userId the user making the request
     * @return number of remaining requests allowed in the current window
     */
    public int getRemainingRequests(UUID userId) {
        if (!redisHealth.isAvailable()) {
            return maxRequests;
        }

        try {
            long currentWindow = System.currentTimeMillis() / (windowDuration.toMillis());
            String key = RATE_KEY_PREFIX + userId + ":" + currentWindow;
            String value = redisTemplate.opsForValue().get(key);
            int used = value != null ? Integer.parseInt(value) : 0;
            return Math.max(0, maxRequests - used);
        } catch (Exception e) {
            return maxRequests;
        }
    }

    public int getMaxRequests() {
        return maxRequests;
    }

    public Duration getWindowDuration() {
        return windowDuration;
    }
}

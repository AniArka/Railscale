package com.railscale.redis.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * Fast Redis availability check used by services to decide whether
 * to use Redis coordination or fall back to DB-only locking.
 * Per Architecture Invariant 5: Redis failure must not cause overselling.
 */
@ConditionalOnBean(org.springframework.data.redis.connection.RedisConnectionFactory.class)
@Component
public class RedisHealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(RedisHealthIndicator.class);

    private final RedisConnectionFactory connectionFactory;

    /**
     * Constructs a new RedisHealthIndicator with the specified RedisConnectionFactory.
     *
     * @param connectionFactory the Redis connection factory
     */
    public RedisHealthIndicator(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    /**
     * Returns true if Redis is reachable. Uses a lightweight PING command.
     * On failure, logs a warning and returns false — services should fall back to DB-only.
     *
     * @return true if Redis is reachable and responds with PONG, false otherwise
     */
    public boolean isAvailable() {
        try {
            String result = connectionFactory.getConnection().ping();
            return "PONG".equals(result);
        } catch (Exception e) {
            log.warn("Redis is unavailable, falling back to DB-only locking: {}", e.getMessage());
            return false;
        }
    }
}

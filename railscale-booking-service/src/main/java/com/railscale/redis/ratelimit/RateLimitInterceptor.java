package com.railscale.redis.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/**
 * Spring MVC interceptor that enforces rate limiting on booking endpoints.
 * Applied to /api/bookings/** via WebMvcConfig.
 * Returns 429 Too Many Requests when the user exceeds the configured limit.
 */
@ConditionalOnBean(org.springframework.data.redis.connection.RedisConnectionFactory.class)
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);

    private final RedisRateLimiter rateLimiter;

    /**
     * Constructs a new RateLimitInterceptor.
     *
     * @param rateLimiter the RedisRateLimiter instance to evaluate request limits
     */
    public RateLimitInterceptor(RedisRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Extract user ID from request header or attribute
        // In a real system this would come from the JWT/security context
        String userIdHeader = request.getHeader("X-User-Id");
        if (userIdHeader == null || userIdHeader.isBlank()) {
            // No user context — skip rate limiting
            return true;
        }

        try {
            UUID userId = UUID.fromString(userIdHeader);

            if (!rateLimiter.isAllowed(userId)) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json");
                response.getWriter().write(
                    "{\"error\":\"RATE_LIMITED\",\"message\":\"Too many booking requests. Please wait before trying again.\",\"retryAfterSeconds\":60}"
                );
                return false;
            }

            // Add remaining requests to response header for client visibility
            int remaining = rateLimiter.getRemainingRequests(userId);
            response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
        } catch (IllegalArgumentException e) {
            // Invalid UUID — skip rate limiting
            log.debug("Invalid X-User-Id header, skipping rate limit: {}", userIdHeader);
        }

        return true;
    }
}

package com.railscale.gateway.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reactive Fallback Controller for Spring Cloud Gateway.
 * Invoked by Resilience4j CircuitBreaker and TimeLimiter filters
 * when downstream microservices are down, slow, or circuit is open.
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @RequestMapping("/users")
    public Mono<ResponseEntity<Map<String, Object>>> userFallback() {
        return Mono.just(createFallbackResponse(
            "user-service",
            "User service is temporarily unavailable. Please try again shortly."
        ));
    }

    @RequestMapping("/search")
    public Mono<ResponseEntity<Map<String, Object>>> searchFallback() {
        return Mono.just(createFallbackResponse(
            "search-service",
            "Search service is temporarily degraded or unavailable. Please try again shortly."
        ));
    }

    @RequestMapping("/bookings")
    public Mono<ResponseEntity<Map<String, Object>>> bookingFallback() {
        return Mono.just(createFallbackResponse(
            "booking-service",
            "Booking service is temporarily unavailable or taking longer than expected. Please verify your booking status before retrying."
        ));
    }

    @RequestMapping("/default")
    public Mono<ResponseEntity<Map<String, Object>>> defaultFallback() {
        return Mono.just(createFallbackResponse(
            "gateway",
            "Service temporarily unavailable. Please try again shortly."
        ));
    }

    private ResponseEntity<Map<String, Object>> createFallbackResponse(String service, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        body.put("error", "Service Unavailable");
        body.put("service", service);
        body.put("message", message);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}

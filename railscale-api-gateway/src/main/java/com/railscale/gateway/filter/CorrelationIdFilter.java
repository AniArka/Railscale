package com.railscale.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Global filter for request correlation IDs (Architecture Section 41).
 * Ensures every incoming request carries X-Request-ID across the distributed system.
 */
@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);
    public static final String CORRELATION_ID_HEADER = "X-Request-ID";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        String correlationId = request.getHeaders().getFirst(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
            log.debug("Generated new {} for request to {}: {}",
                CORRELATION_ID_HEADER, request.getPath(), correlationId);
        } else {
            log.debug("Propagating existing {} for request to {}: {}",
                CORRELATION_ID_HEADER, request.getPath(), correlationId);
        }

        // Mutate request with correlation ID
        ServerHttpRequest mutatedRequest = request.mutate()
            .header(CORRELATION_ID_HEADER, correlationId)
            .build();

        // Add correlation ID to response headers
        exchange.getResponse().getHeaders().add(CORRELATION_ID_HEADER, correlationId);

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}

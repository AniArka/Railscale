package com.railscale.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter that populates the SLF4J MDC with correlation and structured log fields
 * as mandated by Section 41 (Structured Logs & Distributed Tracing).
 *
 * Fields captured:
 * - service: spring.application.name
 * - request_id: from X-Request-ID header or newly generated UUID
 * - user_id: from X-User-ID header (if authenticated via Gateway)
 * - status: HTTP response status code
 * - latency: duration in milliseconds
 * - event: request lifecycle event name
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MdcLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(MdcLoggingFilter.class);

    public static final String HEADER_REQUEST_ID = "X-Request-ID";
    public static final String HEADER_USER_ID = "X-User-ID";

    public static final String MDC_KEY_SERVICE = "service";
    public static final String MDC_KEY_REQUEST_ID = "request_id";
    public static final String MDC_KEY_USER_ID = "user_id";
    public static final String MDC_KEY_STATUS = "status";
    public static final String MDC_KEY_LATENCY = "latency";
    public static final String MDC_KEY_EVENT = "event";

    @Value("${spring.application.name:unknown-service}")
    private String applicationName;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        long startTime = System.currentTimeMillis();

        String requestId = request.getHeader(HEADER_REQUEST_ID);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        String userId = request.getHeader(HEADER_USER_ID);

        try {
            MDC.put(MDC_KEY_SERVICE, applicationName);
            MDC.put(MDC_KEY_REQUEST_ID, requestId);
            if (userId != null && !userId.isBlank()) {
                MDC.put(MDC_KEY_USER_ID, userId);
            }
            MDC.put(MDC_KEY_EVENT, "HTTP_REQUEST_START");

            response.setHeader(HEADER_REQUEST_ID, requestId);

            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            int status = response.getStatus();

            MDC.put(MDC_KEY_STATUS, String.valueOf(status));
            MDC.put(MDC_KEY_LATENCY, String.valueOf(duration));
            MDC.put(MDC_KEY_EVENT, "HTTP_REQUEST_COMPLETED");

            if (status >= 500) {
                log.error("HTTP {} {} returned status {} in {} ms",
                        request.getMethod(), request.getRequestURI(), status, duration);
            } else if (status >= 400) {
                log.warn("HTTP {} {} returned status {} in {} ms",
                        request.getMethod(), request.getRequestURI(), status, duration);
            } else {
                log.info("HTTP {} {} returned status {} in {} ms",
                        request.getMethod(), request.getRequestURI(), status, duration);
            }

            MDC.clear();
        }
    }
}

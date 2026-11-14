package com.railscale.common.logging;

import org.slf4j.Logger;
import org.slf4j.MDC;

import java.util.UUID;

/**
 * Helper to emit structured domain event logs as mandated by Section 41:
 * Fields: timestamp, service, request_id, user_id, booking_id, event, latency, status
 */
public final class StructuredLogger {

    public static final String MDC_KEY_BOOKING_ID = "booking_id";
    public static final String MDC_KEY_EVENT = "event";
    public static final String MDC_KEY_LATENCY = "latency";
    public static final String MDC_KEY_STATUS = "status";
    public static final String MDC_KEY_USER_ID = "user_id";

    private StructuredLogger() {}

    /**
     * Executes a runnable block within a structured domain event logging scope.
     */
    public static void logEvent(Logger log,
                                String event,
                                UUID bookingId,
                                UUID userId,
                                String status,
                                Long latencyMs,
                                String message,
                                Object... args) {
        String oldEvent = MDC.get(MDC_KEY_EVENT);
        String oldBookingId = MDC.get(MDC_KEY_BOOKING_ID);
        String oldUserId = MDC.get(MDC_KEY_USER_ID);
        String oldStatus = MDC.get(MDC_KEY_STATUS);
        String oldLatency = MDC.get(MDC_KEY_LATENCY);

        try {
            if (event != null) MDC.put(MDC_KEY_EVENT, event);
            if (bookingId != null) MDC.put(MDC_KEY_BOOKING_ID, bookingId.toString());
            if (userId != null) MDC.put(MDC_KEY_USER_ID, userId.toString());
            if (status != null) MDC.put(MDC_KEY_STATUS, status);
            if (latencyMs != null) MDC.put(MDC_KEY_LATENCY, String.valueOf(latencyMs));

            log.info(message, args);
        } finally {
            restoreMdc(MDC_KEY_EVENT, oldEvent);
            restoreMdc(MDC_KEY_BOOKING_ID, oldBookingId);
            restoreMdc(MDC_KEY_USER_ID, oldUserId);
            restoreMdc(MDC_KEY_STATUS, oldStatus);
            restoreMdc(MDC_KEY_LATENCY, oldLatency);
        }
    }

    private static void restoreMdc(String key, String value) {
        if (value != null) {
            MDC.put(key, value);
        } else {
            MDC.remove(key);
        }
    }
}

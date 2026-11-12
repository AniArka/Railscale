package com.railscale.common.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StructuredLoggerTest {

    private static final Logger log = LoggerFactory.getLogger(StructuredLoggerTest.class);

    @BeforeEach
    @AfterEach
    void cleanMdc() {
        MDC.clear();
    }

    @Test
    void testStructuredLogEventRestoresPreviousMdc() {
        MDC.put(StructuredLogger.MDC_KEY_EVENT, "PRE_EXISTING_EVENT");

        UUID bookingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        StructuredLogger.logEvent(
                log,
                "TEST_EVENT",
                bookingId,
                userId,
                "200",
                45L,
                "Testing structured logging event with ID {}",
                bookingId
        );

        // Verify MDC was restored to its previous state
        assertEquals("PRE_EXISTING_EVENT", MDC.get(StructuredLogger.MDC_KEY_EVENT));
        assertNull(MDC.get(StructuredLogger.MDC_KEY_BOOKING_ID));
        assertNull(MDC.get(StructuredLogger.MDC_KEY_USER_ID));
        assertNull(MDC.get(StructuredLogger.MDC_KEY_STATUS));
        assertNull(MDC.get(StructuredLogger.MDC_KEY_LATENCY));
    }

    @Test
    void testStructuredLogEventWithNullsDoesNotThrow() {
        assertDoesNotThrow(() ->
                StructuredLogger.logEvent(
                        log,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "Log message with null parameters"
                )
        );
    }
}

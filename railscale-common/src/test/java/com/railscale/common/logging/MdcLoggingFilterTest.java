package com.railscale.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.MDC;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MdcLoggingFilterTest {

    private MdcLoggingFilter filter;

    @BeforeEach
    void setUp() {
        filter = new MdcLoggingFilter();
        ReflectionTestUtils.setField(filter, "applicationName", "test-service");
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void testFilterPropagatesExistingRequestId() throws ServletException, IOException {
        String existingRequestId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getHeader(MdcLoggingFilter.HEADER_REQUEST_ID)).thenReturn(existingRequestId);
        when(request.getHeader(MdcLoggingFilter.HEADER_USER_ID)).thenReturn(userId);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/test");
        when(response.getStatus()).thenReturn(200);

        AtomicReference<String> capturedRequestId = new AtomicReference<>();
        AtomicReference<String> capturedUserId = new AtomicReference<>();
        AtomicReference<String> capturedService = new AtomicReference<>();

        doAnswer(invocation -> {
            capturedRequestId.set(MDC.get(MdcLoggingFilter.MDC_KEY_REQUEST_ID));
            capturedUserId.set(MDC.get(MdcLoggingFilter.MDC_KEY_USER_ID));
            capturedService.set(MDC.get(MdcLoggingFilter.MDC_KEY_SERVICE));
            return null;
        }).when(chain).doFilter(request, response);

        filter.doFilter(request, response, chain);

        assertEquals(existingRequestId, capturedRequestId.get());
        assertEquals(userId, capturedUserId.get());
        assertEquals("test-service", capturedService.get());
        verify(response).setHeader(MdcLoggingFilter.HEADER_REQUEST_ID, existingRequestId);

        // MDC must be cleared after request finishes
        assertNull(MDC.get(MdcLoggingFilter.MDC_KEY_REQUEST_ID));
    }

    @Test
    void testFilterGeneratesRequestIdWhenAbsent() throws ServletException, IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getHeader(MdcLoggingFilter.HEADER_REQUEST_ID)).thenReturn(null);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/test");
        when(response.getStatus()).thenReturn(201);

        AtomicReference<String> capturedRequestId = new AtomicReference<>();

        doAnswer(invocation -> {
            capturedRequestId.set(MDC.get(MdcLoggingFilter.MDC_KEY_REQUEST_ID));
            return null;
        }).when(chain).doFilter(request, response);

        filter.doFilter(request, response, chain);

        assertNotNull(capturedRequestId.get());
        assertDoesNotThrow(() -> UUID.fromString(capturedRequestId.get()));
        verify(response).setHeader(eq(MdcLoggingFilter.HEADER_REQUEST_ID), eq(capturedRequestId.get()));
        assertNull(MDC.get(MdcLoggingFilter.MDC_KEY_REQUEST_ID));
    }
}

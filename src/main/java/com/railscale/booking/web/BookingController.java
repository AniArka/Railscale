package com.railscale.booking.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.railscale.booking.dto.BookingResponse;
import com.railscale.booking.dto.HoldBookingRequest;
import com.railscale.booking.dto.SimulatePaymentRequest;
import com.railscale.booking.service.BookingService;
import com.railscale.reconciliation.ReservationReconciliationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;
    private final ReservationReconciliationService reconciliationService;
    private final ObjectMapper objectMapper;

    public BookingController(
        BookingService bookingService,
        ReservationReconciliationService reconciliationService,
        ObjectMapper objectMapper
    ) {
        this.bookingService = bookingService;
        this.reconciliationService = reconciliationService;
        this.objectMapper = objectMapper;
    }

    /**
     * Section 33: Hold Seats.
     * POST /api/bookings/hold
     */
    @PostMapping("/hold")
    public ResponseEntity<BookingResponse> holdSeats(
        @Valid @RequestBody HoldBookingRequest request,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestHeader(value = "X-User-Id", required = false) UUID headerUserId
    ) {
        UUID effectiveUserId = headerUserId != null ? headerUserId : request.userId();
        String rawPayload = "";
        try {
            rawPayload = objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException ignored) {
        }

        BookingResponse response = bookingService.holdSeats(effectiveUserId, request, idempotencyKey, rawPayload);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Section 33: Confirm / initiate payment.
     * POST /api/bookings/{id}/confirm
     */
    @PostMapping("/{id}/confirm")
    public ResponseEntity<BookingResponse> confirmBooking(
        @PathVariable UUID id,
        @RequestHeader(value = "X-User-Id", required = false) UUID headerUserId
    ) {
        BookingResponse response = bookingService.confirmBooking(id, headerUserId);
        return ResponseEntity.ok(response);
    }

    /**
     * Section 33: Cancel booking.
     * POST /api/bookings/{id}/cancel
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<BookingResponse> cancelBooking(
        @PathVariable UUID id,
        @RequestHeader(value = "X-User-Id", required = false) UUID headerUserId
    ) {
        BookingResponse response = bookingService.cancelBooking(id, headerUserId);
        return ResponseEntity.ok(response);
    }

    /**
     * Section 33: Get booking by ID.
     * GET /api/bookings/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<BookingResponse> getBooking(@PathVariable UUID id) {
        return ResponseEntity.ok(bookingService.getBooking(id));
    }

    /**
     * Get all bookings for a user.
     * GET /api/bookings/user/{userId}
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<BookingResponse>> getBookingsForUser(@PathVariable UUID userId) {
        return ResponseEntity.ok(bookingService.getBookingsForUser(userId));
    }

    /**
     * Phase 2 Payment Simulation:
     * POST /api/bookings/{id}/simulate-payment
     */
    @PostMapping("/{id}/simulate-payment")
    public ResponseEntity<BookingResponse> simulatePayment(
        @PathVariable UUID id,
        @Valid @RequestBody SimulatePaymentRequest request
    ) {
        BookingResponse response = bookingService.processPaymentResult(id, request.status(), request.eventId());
        return ResponseEntity.ok(response);
    }

    /**
     * Trigger reconciliation run on demand:
     * POST /api/bookings/reconciliation/run
     */
    @PostMapping("/reconciliation/run")
    public ResponseEntity<ReservationReconciliationService.ReconciliationReport> triggerReconciliation() {
        ReservationReconciliationService.ReconciliationReport report = reconciliationService.runReconciliation();
        return ResponseEntity.ok(report);
    }
}

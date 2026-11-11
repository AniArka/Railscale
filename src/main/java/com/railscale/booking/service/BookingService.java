package com.railscale.booking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.railscale.booking.domain.Booking;
import com.railscale.booking.domain.BookingItem;
import com.railscale.booking.domain.BookingStatus;
import com.railscale.booking.dto.BookingResponse;
import com.railscale.booking.dto.HoldBookingRequest;
import com.railscale.booking.repository.BookingItemRepository;
import com.railscale.booking.repository.BookingRepository;
import com.railscale.common.exception.BadRequestException;
import com.railscale.common.exception.ConflictException;
import com.railscale.common.exception.ResourceNotFoundException;
import com.railscale.event.domain.ProcessedEvent;
import com.railscale.event.domain.ProcessedEventId;
import com.railscale.event.repository.ProcessedEventRepository;
import com.railscale.idempotency.service.IdempotencyService;
import com.railscale.inventory.domain.SeatInventory;
import com.railscale.inventory.domain.SeatStatus;
import com.railscale.inventory.repository.SeatInventoryRepository;
import com.railscale.outbox.domain.OutboxEvent;
import com.railscale.outbox.repository.OutboxEventRepository;
import com.railscale.payment.domain.Payment;
import com.railscale.payment.domain.PaymentStatus;
import com.railscale.payment.repository.PaymentRepository;
import com.railscale.train.domain.CoachType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final BookingRepository bookingRepository;
    private final BookingItemRepository bookingItemRepository;
    private final SeatInventoryRepository seatInventoryRepository;
    private final PaymentRepository paymentRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    private final Duration holdTimeout;
    private final Duration paymentTimeout;

    public BookingService(
        BookingRepository bookingRepository,
        BookingItemRepository bookingItemRepository,
        SeatInventoryRepository seatInventoryRepository,
        PaymentRepository paymentRepository,
        OutboxEventRepository outboxEventRepository,
        ProcessedEventRepository processedEventRepository,
        IdempotencyService idempotencyService,
        ObjectMapper objectMapper,
        @Value("${railscale.reservation.hold-timeout:5m}") Duration holdTimeout,
        @Value("${railscale.reservation.payment-timeout:2m}") Duration paymentTimeout
    ) {
        this.bookingRepository = bookingRepository;
        this.bookingItemRepository = bookingItemRepository;
        this.seatInventoryRepository = seatInventoryRepository;
        this.paymentRepository = paymentRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.processedEventRepository = processedEventRepository;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
        this.holdTimeout = holdTimeout;
        this.paymentTimeout = paymentTimeout;
    }

    /**
     * Section 34 & 35: Critical Booking Hold Transaction.
     * Locks inventory rows deterministically with FOR UPDATE and validates availability.
     */
    @Transactional
    public BookingResponse holdSeats(
        UUID userId,
        HoldBookingRequest request,
        String idempotencyKey,
        String rawPayload
    ) {
        if (userId == null) {
            userId = request.userId();
        }
        if (userId == null) {
            throw new BadRequestException("User ID is required for hold operation");
        }

        // 1. Atomic idempotency gate
        IdempotencyService.GateResult gate = idempotencyService.enterGate(userId, idempotencyKey, rawPayload);
        if (!gate.ownsProcessing()) {
            try {
                return objectMapper.readValue(gate.cachedBody(), BookingResponse.class);
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize cached response for idempotency key: {}", idempotencyKey, e);
            }
        }

        // 2. Sort identifiers deterministically before database query
        List<UUID> requestedSeatIds = request.seatIds().stream()
            .distinct()
            .sorted()
            .toList();

        // 3. Pessimistic write lock in canonical inventory ID order
        List<SeatInventory> lockedInventory = seatInventoryRepository
            .findByJourneyIdAndSeatIdInForUpdate(request.journeyId(), requestedSeatIds);

        // If seatIds were inventory UUIDs instead of physical seat UUIDs, fallback to findAllForUpdate
        if (lockedInventory.size() < requestedSeatIds.size()) {
            List<SeatInventory> byInventoryId = seatInventoryRepository.findAllForUpdate(requestedSeatIds);
            if (byInventoryId.size() == requestedSeatIds.size()
                && byInventoryId.stream().allMatch(inv -> inv.getJourneyId().equals(request.journeyId()))) {
                lockedInventory = byInventoryId;
            }
        }

        if (lockedInventory.size() < requestedSeatIds.size()) {
            throw new ResourceNotFoundException("One or more requested seats could not be found for the journey");
        }

        // Ensure canonical sorting by inventory ID
        lockedInventory = lockedInventory.stream()
            .sorted(Comparator.comparing(SeatInventory::getId))
            .toList();

        // 4. Validate that all seats are AVAILABLE and have no active hold
        Instant now = Instant.now();
        Instant heldUntil = now.plus(holdTimeout);

        for (SeatInventory inventory : lockedInventory) {
            if (inventory.getStatus() != SeatStatus.AVAILABLE) {
                throw new ConflictException(
                    "SEAT_UNAVAILABLE: Seat " + (inventory.getSeat() != null ? inventory.getSeat().getSeatNumber() : inventory.getId())
                        + " is not available (Current status: " + inventory.getStatus() + ")"
                );
            }
            if (inventory.getHeldUntil() != null && inventory.getHeldUntil().isAfter(now)) {
                throw new ConflictException("SEAT_UNAVAILABLE: Seat is currently held by another user");
            }
        }

        // 5. Calculate total amount
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (SeatInventory inventory : lockedInventory) {
            totalAmount = totalAmount.add(determineSeatPrice(inventory));
        }

        // 6. Create Booking
        Booking booking = new Booking(userId, request.journeyId(), totalAmount);
        booking.setStatus(BookingStatus.HELD);
        booking = bookingRepository.save(booking);

        // 7. Create BookingItems & update SeatInventory
        List<BookingItem> items = new ArrayList<>(lockedInventory.size());
        for (SeatInventory inventory : lockedInventory) {
            BigDecimal price = determineSeatPrice(inventory);
            BookingItem item = new BookingItem(booking, inventory, price);
            booking.addItem(item);
            items.add(item);

            // Invariant 3 & 4: HELD status requires held_by and held_until
            inventory.setStatus(SeatStatus.HELD);
            inventory.setHeldBy(userId);
            inventory.setHeldUntil(heldUntil);
            inventory.setVersion(inventory.getVersion() + 1);
            seatInventoryRepository.save(inventory);
        }
        bookingItemRepository.saveAll(items);

        // 8. Invariant 6: Create outbox event in the same transaction
        Map<String, Object> outboxPayload = new LinkedHashMap<>();
        outboxPayload.put("bookingId", booking.getId());
        outboxPayload.put("userId", userId);
        outboxPayload.put("journeyId", request.journeyId());
        outboxPayload.put("seatInventoryIds", lockedInventory.stream().map(SeatInventory::getId).toList());
        outboxPayload.put("expiresAt", heldUntil);
        outboxPayload.put("totalAmount", totalAmount);

        OutboxEvent outboxEvent = new OutboxEvent(
            "Booking",
            booking.getId(),
            "BookingHeld",
            toJson(outboxPayload)
        );
        outboxEventRepository.save(outboxEvent);

        BookingResponse response = BookingResponse.fromEntity(booking, heldUntil);

        // 9. Store idempotent response
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotencyService.recordResponse(userId, idempotencyKey, 201, toJson(response));
        }

        return response;
    }

    /**
     * Section 20 & Section 30: Booking confirmation / payment initiation.
     * Mandatory lease extension to payment timeout.
     */
    @Transactional
    public BookingResponse confirmBooking(UUID bookingId, UUID userId) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
            .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));

        if (userId != null && !booking.getUserId().equals(userId)) {
            throw new BadRequestException("Booking does not belong to user");
        }

        if (booking.getStatus() != BookingStatus.HELD) {
            throw new ConflictException("Booking cannot be confirmed from status: " + booking.getStatus());
        }

        // Lock associated inventory in deterministic canonical ID order
        List<SeatInventory> seats = seatInventoryRepository.findByBookingIdForUpdate(bookingId);
        Instant now = Instant.now();

        for (SeatInventory seat : seats) {
            if (seat.getStatus() != SeatStatus.HELD || !booking.getUserId().equals(seat.getHeldBy())) {
                throw new ConflictException("Seat hold mismatch or already released for seat: " + seat.getId());
            }
            if (seat.getHeldUntil() != null && seat.getHeldUntil().isBefore(now)) {
                throw new ConflictException("Hold expired before confirmation could begin");
            }
        }

        // Mandatory lease extension to payment timeout
        Instant paymentDeadline = now.plus(paymentTimeout);
        for (SeatInventory seat : seats) {
            seat.setHeldUntil(paymentDeadline);
            seat.setVersion(seat.getVersion() + 1);
            seatInventoryRepository.save(seat);
        }

        booking.setStatus(BookingStatus.PAYMENT_PENDING);
        booking.setPaymentInitiatedAt(now);
        bookingRepository.save(booking);

        // Record payment in PENDING status
        Payment payment = paymentRepository.findByBookingId(bookingId)
            .orElseGet(() -> new Payment(bookingId, PaymentStatus.PENDING, booking.getTotalAmount(), "SIM-" + UUID.randomUUID()));
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(booking.getTotalAmount());
        paymentRepository.save(payment);

        // Create PaymentInitiated outbox event
        Map<String, Object> outboxPayload = Map.of(
            "bookingId", booking.getId(),
            "userId", booking.getUserId(),
            "paymentDeadline", paymentDeadline,
            "amount", booking.getTotalAmount()
        );
        outboxEventRepository.save(new OutboxEvent(
            "Booking",
            booking.getId(),
            "PaymentInitiated",
            toJson(outboxPayload)
        ));

        return BookingResponse.fromEntity(booking, paymentDeadline);
    }

    /**
     * Section 10, 21, 22: Payment completed/failed processing.
     * Invariant 7: Deduplication and state transition commit in the same transaction.
     * Invariant 9: Late payment after expiry emits RefundRequired.
     */
    @Transactional
    public BookingResponse processPaymentResult(UUID bookingId, PaymentStatus resultStatus, UUID eventId) {
        // Consumer deduplication gate — uses standard JPA, works on both PostgreSQL and H2
        if (eventId != null) {
            ProcessedEventId peId = new ProcessedEventId("payment-result-consumer", eventId);
            if (processedEventRepository.existsById(peId)) {
                log.info("Duplicate payment event ignored: {}", eventId);
                return bookingRepository.findWithItemsById(bookingId)
                    .map(BookingResponse::fromEntity)
                    .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));
            }
            try {
                processedEventRepository.saveAndFlush(new ProcessedEvent("payment-result-consumer", eventId, Instant.now()));
            } catch (DataIntegrityViolationException e) {
                log.info("Duplicate payment event ignored (race): {}", eventId);
                return bookingRepository.findWithItemsById(bookingId)
                    .map(BookingResponse::fromEntity)
                    .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));
            }
        }

        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
            .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));

        if (booking.getStatus() == BookingStatus.PAYMENT_PENDING) {
            if (resultStatus == PaymentStatus.SUCCESS) {
                List<SeatInventory> seats = seatInventoryRepository.findByBookingIdForUpdate(bookingId);
                for (SeatInventory seat : seats) {
                    // Invariant 4: Non-held seats must clear hold metadata
                    seat.setStatus(SeatStatus.BOOKED);
                    seat.setHeldBy(null);
                    seat.setHeldUntil(null);
                    seat.setVersion(seat.getVersion() + 1);
                    seatInventoryRepository.save(seat);
                }

                booking.setStatus(BookingStatus.CONFIRMED);
                bookingRepository.save(booking);

                paymentRepository.findByBookingId(bookingId).ifPresent(p -> {
                    p.setStatus(PaymentStatus.SUCCESS);
                    paymentRepository.save(p);
                });

                outboxEventRepository.save(new OutboxEvent(
                    "Booking",
                    booking.getId(),
                    "BookingConfirmed",
                    toJson(Map.of("bookingId", booking.getId(), "userId", booking.getUserId()))
                ));
            } else {
                // Payment failed: release seats to AVAILABLE
                List<SeatInventory> seats = seatInventoryRepository.findByBookingIdForUpdate(bookingId);
                for (SeatInventory seat : seats) {
                    seat.setStatus(SeatStatus.AVAILABLE);
                    seat.setHeldBy(null);
                    seat.setHeldUntil(null);
                    seat.setVersion(seat.getVersion() + 1);
                    seatInventoryRepository.save(seat);
                }

                booking.setStatus(BookingStatus.FAILED);
                bookingRepository.save(booking);

                paymentRepository.findByBookingId(bookingId).ifPresent(p -> {
                    p.setStatus(PaymentStatus.FAILED);
                    paymentRepository.save(p);
                });

                outboxEventRepository.save(new OutboxEvent(
                    "Booking",
                    booking.getId(),
                    "PaymentFailed",
                    toJson(Map.of("bookingId", booking.getId()))
                ));
                outboxEventRepository.save(new OutboxEvent(
                    "Inventory",
                    booking.getId(),
                    "SeatReleased",
                    toJson(Map.of("bookingId", booking.getId()))
                ));
            }
        } else if (booking.getStatus() == BookingStatus.EXPIRED) {
            // CRITICAL INVARIANT 9 & Section 10: Late payment after expiry
            if (resultStatus == PaymentStatus.SUCCESS) {
                log.warn("Late payment arrived for expired booking {}. Emitting RefundRequired.", bookingId);

                Payment payment = paymentRepository.findByBookingId(bookingId)
                    .orElseGet(() -> new Payment(bookingId, PaymentStatus.REFUND_PENDING, booking.getTotalAmount(), "LATE-PAY"));
                payment.setStatus(PaymentStatus.REFUND_PENDING);
                paymentRepository.save(payment);

                outboxEventRepository.save(new OutboxEvent(
                    "Payment",
                    booking.getId(),
                    "RefundRequired",
                    toJson(Map.of(
                        "bookingId", booking.getId(),
                        "amount", booking.getTotalAmount(),
                        "reason", "PAYMENT_ARRIVED_AFTER_EXPIRY"
                    ))
                ));
            }
        }

        return BookingResponse.fromEntity(booking);
    }

    /**
     * Section 11 & Section 24: Cancellation and Refund.
     */
    @Transactional
    public BookingResponse cancelBooking(UUID bookingId, UUID userId) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
            .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));

        if (userId != null && !booking.getUserId().equals(userId)) {
            throw new BadRequestException("Booking does not belong to user");
        }

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            return BookingResponse.fromEntity(booking);
        }

        List<SeatInventory> seats = seatInventoryRepository.findByBookingIdForUpdate(bookingId);

        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            booking.setStatus(BookingStatus.CANCELLED);
            bookingRepository.save(booking);

            for (SeatInventory seat : seats) {
                seat.setStatus(SeatStatus.AVAILABLE);
                seat.setHeldBy(null);
                seat.setHeldUntil(null);
                seat.setVersion(seat.getVersion() + 1);
                seatInventoryRepository.save(seat);
            }

            paymentRepository.findByBookingId(bookingId).ifPresent(p -> {
                p.setStatus(PaymentStatus.REFUND_PENDING);
                paymentRepository.save(p);
            });

            outboxEventRepository.save(new OutboxEvent(
                "Payment",
                booking.getId(),
                "RefundRequired",
                toJson(Map.of("bookingId", booking.getId(), "amount", booking.getTotalAmount(), "reason", "CANCELLATION"))
            ));
            outboxEventRepository.save(new OutboxEvent(
                "Booking",
                booking.getId(),
                "BookingCancelled",
                toJson(Map.of("bookingId", booking.getId()))
            ));
            outboxEventRepository.save(new OutboxEvent(
                "Inventory",
                booking.getId(),
                "SeatReleased",
                toJson(Map.of("bookingId", booking.getId()))
            ));
        } else if (booking.getStatus() == BookingStatus.HELD || booking.getStatus() == BookingStatus.PAYMENT_PENDING) {
            booking.setStatus(BookingStatus.CANCELLED);
            bookingRepository.save(booking);

            for (SeatInventory seat : seats) {
                seat.setStatus(SeatStatus.AVAILABLE);
                seat.setHeldBy(null);
                seat.setHeldUntil(null);
                seat.setVersion(seat.getVersion() + 1);
                seatInventoryRepository.save(seat);
            }

            outboxEventRepository.save(new OutboxEvent(
                "Inventory",
                booking.getId(),
                "SeatReleased",
                toJson(Map.of("bookingId", booking.getId()))
            ));
        } else {
            throw new ConflictException("Booking in status " + booking.getStatus() + " cannot be cancelled");
        }

        return BookingResponse.fromEntity(booking);
    }

    @Transactional(readOnly = true)
    public BookingResponse getBooking(UUID bookingId) {
        Booking booking = bookingRepository.findWithItemsById(bookingId)
            .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));
        return BookingResponse.fromEntity(booking);
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> getBookingsForUser(UUID userId) {
        return bookingRepository.findByUserId(userId).stream()
            .map(BookingResponse::fromEntity)
            .toList();
    }

    private BigDecimal determineSeatPrice(SeatInventory inventory) {
        if (inventory.getSeat() != null && inventory.getSeat().getCoach() != null) {
            CoachType type = inventory.getSeat().getCoach().getCoachType();
            if (type != null) {
                return switch (type) {
                    case AC_TIER_1 -> BigDecimal.valueOf(1500.00);
                    case AC_TIER_2 -> BigDecimal.valueOf(1000.00);
                    case AC_TIER_3 -> BigDecimal.valueOf(750.00);
                    case CHAIR_CAR -> BigDecimal.valueOf(500.00);
                    case SLEEPER -> BigDecimal.valueOf(400.00);
                };
            }
        }
        return BigDecimal.valueOf(500.00);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize to JSON", e);
        }
    }
}

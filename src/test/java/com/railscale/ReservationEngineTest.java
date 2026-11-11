package com.railscale;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.railscale.booking.domain.Booking;
import com.railscale.booking.domain.BookingStatus;
import com.railscale.booking.dto.BookingResponse;
import com.railscale.booking.dto.HoldBookingRequest;
import com.railscale.booking.repository.BookingRepository;
import com.railscale.booking.service.BookingService;
import com.railscale.common.exception.ConflictException;
import com.railscale.event.repository.ProcessedEventRepository;
import com.railscale.idempotency.repository.IdempotencyKeyRepository;
import com.railscale.idempotency.service.IdempotencyService;
import com.railscale.inventory.domain.SeatInventory;
import com.railscale.inventory.domain.SeatStatus;
import com.railscale.inventory.repository.SeatInventoryRepository;
import com.railscale.journey.domain.Journey;
import com.railscale.journey.repository.JourneyRepository;
import com.railscale.outbox.domain.OutboxEvent;
import com.railscale.outbox.repository.OutboxEventRepository;
import com.railscale.payment.domain.PaymentStatus;
import com.railscale.payment.repository.PaymentRepository;
import com.railscale.reconciliation.ReservationReconciliationService;
import com.railscale.train.domain.BerthType;
import com.railscale.train.domain.Coach;
import com.railscale.train.domain.CoachType;
import com.railscale.train.domain.Seat;
import com.railscale.train.domain.Station;
import com.railscale.train.domain.Train;
import com.railscale.train.repository.CoachRepository;
import com.railscale.train.repository.SeatRepository;
import com.railscale.train.repository.StationRepository;
import com.railscale.train.repository.TrainRepository;
import com.railscale.user.domain.Role;
import com.railscale.user.domain.User;
import com.railscale.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ReservationEngineTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private ReservationReconciliationService reconciliationService;

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private SeatInventoryRepository seatInventoryRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private StationRepository stationRepository;

    @Autowired
    private TrainRepository trainRepository;

    @Autowired
    private CoachRepository coachRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private JourneyRepository journeyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private User user1;
    private User user2;
    private Journey journey;
    private Seat seatA;
    private Seat seatB;
    private SeatInventory invA;
    private SeatInventory invB;

    @BeforeEach
    void setUp() {
        // Delete in FK-safe order: children before parents
        processedEventRepository.deleteAll();
        idempotencyKeyRepository.deleteAll();
        outboxEventRepository.deleteAll();
        paymentRepository.deleteAll();
        bookingRepository.deleteAll();
        seatInventoryRepository.deleteAll();
        journeyRepository.deleteAll();
        seatRepository.deleteAll();
        coachRepository.deleteAll();
        trainRepository.deleteAll();
        stationRepository.deleteAll();
        userRepository.deleteAll();

        user1 = userRepository.save(new User("user1@example.com", "pass", "User One", Role.USER));
        user2 = userRepository.save(new User("user2@example.com", "pass", "User Two", Role.USER));

        Station src = stationRepository.save(new Station("HWH", "Howrah", "Kolkata"));
        Station dest = stationRepository.save(new Station("NDLS", "New Delhi", "Delhi"));
        Train train = trainRepository.save(new Train("RS-101", "RailScale Express"));
        Coach coach = coachRepository.save(new Coach(train, "B1", CoachType.AC_TIER_3));

        seatA = seatRepository.save(new Seat(coach, "1", BerthType.LOWER));
        seatB = seatRepository.save(new Seat(coach, "2", BerthType.MIDDLE));

        Instant departure = Instant.now().plus(2, ChronoUnit.DAYS);
        journey = journeyRepository.save(new Journey(train, src, dest, departure, departure.plus(12, ChronoUnit.HOURS)));

        invA = seatInventoryRepository.save(new SeatInventory(journey.getId(), seatA));
        invB = seatInventoryRepository.save(new SeatInventory(journey.getId(), seatB));
    }

    @Test
    @DisplayName("Critical Test 1: Concurrency - 1 seat, concurrent hold attempts: exactly 1 succeeds, overselling = 0")
    void concurrentHoldLastSeatMustNotOversell() throws Exception {
        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final int idx = i;
            tasks.add(() -> {
                startLatch.await();
                try {
                    HoldBookingRequest req = new HoldBookingRequest(journey.getId(), List.of(seatA.getId()), user1.getId());
                    bookingService.holdSeats(user1.getId(), req, "concurrent-key-" + idx, "{\"attempt\":" + idx + "}");
                    successCount.incrementAndGet();
                } catch (ConflictException e) {
                    conflictCount.incrementAndGet();
                }
                return null;
            });
        }

        startLatch.countDown();
        for (Future<Void> future : executor.invokeAll(tasks)) {
            future.get();
        }
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(threads - 1);

        SeatInventory finalInv = seatInventoryRepository.findById(invA.getId()).orElseThrow();
        assertThat(finalInv.getStatus()).isEqualTo(SeatStatus.HELD);
        assertThat(finalInv.getHeldBy()).isNotNull();
        assertThat(finalInv.getHeldUntil()).isNotNull();
    }

    @Test
    @DisplayName("Critical Test 2 & Invariant 10: Idempotency - Same key + same payload creates 1 booking and returns cached response")
    void idempotencyReplayReturnsIdenticalBooking() throws Exception {
        String key = "test-idemp-" + UUID.randomUUID();
        HoldBookingRequest req = new HoldBookingRequest(journey.getId(), List.of(seatA.getId()), user1.getId());
        String payload = objectMapper.writeValueAsString(req);

        // First attempt
        BookingResponse resp1 = bookingService.holdSeats(user1.getId(), req, key, payload);
        assertThat(resp1).isNotNull();
        assertThat(resp1.status()).isEqualTo(BookingStatus.HELD);

        // Second attempt with exact same key and payload
        BookingResponse resp2 = bookingService.holdSeats(user1.getId(), req, key, payload);
        assertThat(resp2.id()).isEqualTo(resp1.id());
        assertThat(resp2.status()).isEqualTo(BookingStatus.HELD);
        assertThat(resp2.totalAmount()).isEqualByComparingTo(resp1.totalAmount());

        // Exactly 1 booking in database
        assertThat(bookingRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Critical Test 4: Idempotency Key Reuse - Same key + different payload throws 409 IDEMPOTENCY_KEY_REUSED")
    void idempotencyKeyReuseThrowsConflict() throws Exception {
        String key = "reuse-key-" + UUID.randomUUID();
        HoldBookingRequest req1 = new HoldBookingRequest(journey.getId(), List.of(seatA.getId()), user1.getId());
        HoldBookingRequest req2 = new HoldBookingRequest(journey.getId(), List.of(seatB.getId()), user1.getId());

        bookingService.holdSeats(user1.getId(), req1, key, objectMapper.writeValueAsString(req1));

        assertThatThrownBy(() -> bookingService.holdSeats(user1.getId(), req2, key, objectMapper.writeValueAsString(req2)))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    @DisplayName("Critical Test 3: In-flight duplicate detection - Request while in progress throws 409 REQUEST_IN_PROGRESS")
    void inFlightDuplicateThrowsRequestInProgress() {
        String key = "inflight-key-" + UUID.randomUUID();
        String payload = "{\"test\":\"inflight\"}";

        // First enter gate succeeds
        IdempotencyService.GateResult gate1 = idempotencyService.enterGate(user1.getId(), key, payload);
        assertThat(gate1.ownsProcessing()).isTrue();

        // Second attempt before response is recorded
        assertThatThrownBy(() -> idempotencyService.enterGate(user1.getId(), key, payload))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("REQUEST_IN_PROGRESS");
    }

    @Test
    @DisplayName("Section 20: Booking confirmation extends seat lease to payment timeout")
    void confirmBookingExtendsSeatLease() throws Exception {
        HoldBookingRequest req = new HoldBookingRequest(journey.getId(), List.of(seatA.getId()), user1.getId());
        BookingResponse holdResp = bookingService.holdSeats(user1.getId(), req, "confirm-key-1", "{}");

        BookingResponse confirmResp = bookingService.confirmBooking(holdResp.id(), user1.getId());
        assertThat(confirmResp.status()).isEqualTo(BookingStatus.PAYMENT_PENDING);
        assertThat(confirmResp.paymentInitiatedAt()).isNotNull();

        SeatInventory seat = seatInventoryRepository.findById(invA.getId()).orElseThrow();
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.HELD);
        assertThat(seat.getHeldUntil()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("Section 21 & Invariant 9: Payment completed success confirms booking and transitions seats to BOOKED")
    void paymentCompletedSuccessConfirmsBooking() throws Exception {
        HoldBookingRequest req = new HoldBookingRequest(journey.getId(), List.of(seatA.getId()), user1.getId());
        BookingResponse holdResp = bookingService.holdSeats(user1.getId(), req, "pay-key-1", "{}");
        bookingService.confirmBooking(holdResp.id(), user1.getId());

        UUID eventId = UUID.randomUUID();
        BookingResponse finalResp = bookingService.processPaymentResult(holdResp.id(), PaymentStatus.SUCCESS, eventId);
        assertThat(finalResp.status()).isEqualTo(BookingStatus.CONFIRMED);

        SeatInventory seat = seatInventoryRepository.findById(invA.getId()).orElseThrow();
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.BOOKED);
        // Invariant 4: Non-held seats must have null hold metadata
        assertThat(seat.getHeldBy()).isNull();
        assertThat(seat.getHeldUntil()).isNull();
    }

    @Test
    @DisplayName("Critical Test 10 & Invariant 9: Late payment after expiry produces RefundRequired and does not confirm")
    void latePaymentAfterExpiryProducesRefundRequired() throws Exception {
        HoldBookingRequest req = new HoldBookingRequest(journey.getId(), List.of(seatA.getId()), user1.getId());
        BookingResponse holdResp = bookingService.holdSeats(user1.getId(), req, "late-key-1", "{}");
        bookingService.confirmBooking(holdResp.id(), user1.getId());

        // Simulate expiration of booking and seat release
        Booking booking = bookingRepository.findById(holdResp.id()).orElseThrow();
        booking.setStatus(BookingStatus.EXPIRED);
        bookingRepository.save(booking);

        SeatInventory seat = seatInventoryRepository.findById(invA.getId()).orElseThrow();
        seat.setStatus(SeatStatus.AVAILABLE);
        seat.setHeldBy(null);
        seat.setHeldUntil(null);
        seatInventoryRepository.save(seat);

        // Now deliver PaymentCompleted (SUCCESS)
        BookingResponse result = bookingService.processPaymentResult(booking.getId(), PaymentStatus.SUCCESS, UUID.randomUUID());
        assertThat(result.status()).isEqualTo(BookingStatus.EXPIRED);

        // Booking remains EXPIRED, seat remains AVAILABLE
        SeatInventory updatedSeat = seatInventoryRepository.findById(invA.getId()).orElseThrow();
        assertThat(updatedSeat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);

        // Verify RefundRequired event in outbox
        List<OutboxEvent> outbox = outboxEventRepository.findAll();
        assertThat(outbox).anyMatch(e -> e.getEventType().equals("RefundRequired"));
    }

    @Test
    @DisplayName("Critical Test 5: Hold Expiration Reconciliation releases expired seats to AVAILABLE")
    void holdExpirationReconciliationReleasesSeats() throws Exception {
        HoldBookingRequest req = new HoldBookingRequest(journey.getId(), List.of(seatA.getId()), user1.getId());
        BookingResponse holdResp = bookingService.holdSeats(user1.getId(), req, "rec-key-1", "{}");

        // Manually age the hold deadline to past
        SeatInventory seat = seatInventoryRepository.findById(invA.getId()).orElseThrow();
        seat.setHeldUntil(Instant.now().minus(10, ChronoUnit.MINUTES));
        seatInventoryRepository.save(seat);

        // Run reconciliation
        int expired = reconciliationService.expireHolds();
        assertThat(expired).isGreaterThanOrEqualTo(1);

        SeatInventory updatedSeat = seatInventoryRepository.findById(invA.getId()).orElseThrow();
        assertThat(updatedSeat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(updatedSeat.getHeldBy()).isNull();
        assertThat(updatedSeat.getHeldUntil()).isNull();

        Booking updatedBooking = bookingRepository.findById(holdResp.id()).orElseThrow();
        assertThat(updatedBooking.getStatus()).isEqualTo(BookingStatus.EXPIRED);
    }

    @Test
    @DisplayName("Payment Timeout Reconciliation releases inventory and marks booking EXPIRED")
    void paymentTimeoutReconciliationExpiresBooking() throws Exception {
        HoldBookingRequest req = new HoldBookingRequest(journey.getId(), List.of(seatA.getId()), user1.getId());
        BookingResponse holdResp = bookingService.holdSeats(user1.getId(), req, "rec-pay-key-1", "{}");
        bookingService.confirmBooking(holdResp.id(), user1.getId());

        // Manually age paymentInitiatedAt to past timeout
        Booking booking = bookingRepository.findById(holdResp.id()).orElseThrow();
        booking.setPaymentInitiatedAt(Instant.now().minus(10, ChronoUnit.MINUTES));
        bookingRepository.save(booking);

        int expired = reconciliationService.expirePaymentPendingBookings();
        assertThat(expired).isGreaterThanOrEqualTo(1);

        Booking updatedBooking = bookingRepository.findById(holdResp.id()).orElseThrow();
        assertThat(updatedBooking.getStatus()).isEqualTo(BookingStatus.EXPIRED);

        SeatInventory updatedSeat = seatInventoryRepository.findById(invA.getId()).orElseThrow();
        assertThat(updatedSeat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(updatedSeat.getHeldBy()).isNull();
        assertThat(updatedSeat.getHeldUntil()).isNull();
    }

    @Test
    @DisplayName("Section 11 & Section 24: Confirmed booking cancellation releases seat and emits RefundRequired")
    void cancellationOfConfirmedBookingReleasesSeatAndEmitsRefund() throws Exception {
        HoldBookingRequest req = new HoldBookingRequest(journey.getId(), List.of(seatA.getId()), user1.getId());
        BookingResponse holdResp = bookingService.holdSeats(user1.getId(), req, "cancel-key-1", "{}");
        bookingService.confirmBooking(holdResp.id(), user1.getId());
        bookingService.processPaymentResult(holdResp.id(), PaymentStatus.SUCCESS, UUID.randomUUID());

        BookingResponse cancelResp = bookingService.cancelBooking(holdResp.id(), user1.getId());
        assertThat(cancelResp.status()).isEqualTo(BookingStatus.CANCELLED);

        SeatInventory updatedSeat = seatInventoryRepository.findById(invA.getId()).orElseThrow();
        assertThat(updatedSeat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);

        List<OutboxEvent> events = outboxEventRepository.findAll();
        assertThat(events).anyMatch(e -> e.getEventType().equals("RefundRequired"));
        assertThat(events).anyMatch(e -> e.getEventType().equals("BookingCancelled"));
        assertThat(events).anyMatch(e -> e.getEventType().equals("SeatReleased"));
    }
}

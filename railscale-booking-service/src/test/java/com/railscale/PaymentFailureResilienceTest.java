package com.railscale;

import com.railscale.booking.domain.Booking;
import com.railscale.booking.domain.BookingStatus;
import com.railscale.booking.dto.BookingResponse;
import com.railscale.booking.dto.HoldBookingRequest;
import com.railscale.booking.repository.BookingItemRepository;
import com.railscale.booking.repository.BookingRepository;
import com.railscale.booking.service.BookingService;
import com.railscale.event.repository.ProcessedEventRepository;
import com.railscale.idempotency.repository.IdempotencyKeyRepository;
import com.railscale.inventory.domain.SeatInventory;
import com.railscale.inventory.domain.SeatStatus;
import com.railscale.inventory.repository.SeatInventoryRepository;
import com.railscale.journey.domain.Journey;
import com.railscale.journey.repository.JourneyRepository;
import com.railscale.outbox.domain.OutboxEvent;
import com.railscale.outbox.repository.OutboxEventRepository;
import com.railscale.payment.domain.Payment;
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

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 9 Chaos Test: Payment Failure & Resilience Test (Section 10, 23, 26, 52).
 *
 * Verifies:
 * 1. Explicit Payment Processing Failure:
 *    - User holds seats -> PAYMENT_PENDING. Payment fails -> booking transitions to FAILED.
 *    - Inventory releases back to AVAILABLE with hold metadata wiped.
 *    - Another user can immediately book the released seats.
 * 2. Payment Timeout & Abandonment Reconciliation:
 *    - Uncompleted PAYMENT_PENDING bookings are expired by reconciliation service.
 *    - Inventory released back to AVAILABLE.
 * 3. Late Payment Arrival After Expiry (Critical Invariant 9 & Section 10):
 *    - Payment arrives SUCCESS for an already EXPIRED booking.
 *    - Booking does NOT resurrect; remains EXPIRED.
 *    - Emits RefundRequired event, payment marked REFUND_PENDING.
 * 4. Idempotent Payment Webhook Delivery:
 *    - Duplicate payment event replay does not corrupt state or re-emit confirmation events.
 */
@SpringBootTest
@ActiveProfiles("test")
class PaymentFailureResilienceTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private ReservationReconciliationService reconciliationService;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Autowired
    private UserRepository userRepository;

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
    private SeatInventoryRepository seatInventoryRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingItemRepository bookingItemRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    private User primaryUser;
    private User secondaryUser;
    private Journey journey;
    private Seat seat1;
    private Seat seat2;
    private SeatInventory inventory1;
    private SeatInventory inventory2;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
        processedEventRepository.deleteAll();
        paymentRepository.deleteAll();
        bookingItemRepository.deleteAll();
        bookingRepository.deleteAll();
        seatInventoryRepository.deleteAll();
        journeyRepository.deleteAll();
        seatRepository.deleteAll();
        coachRepository.deleteAll();
        trainRepository.deleteAll();
        stationRepository.deleteAll();
        idempotencyKeyRepository.deleteAll();
        userRepository.deleteAll();

        primaryUser = userRepository.save(new User("primary@test.com", "pass", "Primary User", Role.USER));
        secondaryUser = userRepository.save(new User("secondary@test.com", "pass", "Secondary User", Role.USER));

        Station src = stationRepository.save(new Station("DEL", "Delhi", "Delhi"));
        Station dst = stationRepository.save(new Station("BOM", "Mumbai", "Mumbai"));
        Train train = trainRepository.save(new Train("KA-200", "Payment Resilience Express"));
        Coach coach = coachRepository.save(new Coach(train, "B1", CoachType.AC_TIER_3));

        seat1 = seatRepository.save(new Seat(coach, "1A", BerthType.LOWER));
        seat2 = seatRepository.save(new Seat(coach, "1B", BerthType.UPPER));

        Instant dep = Instant.now().plus(3, ChronoUnit.DAYS);
        journey = journeyRepository.save(new Journey(train, src, dst, dep, dep.plus(16, ChronoUnit.HOURS)));

        inventory1 = seatInventoryRepository.save(new SeatInventory(journey.getId(), seat1));
        inventory2 = seatInventoryRepository.save(new SeatInventory(journey.getId(), seat2));
    }

    @Test
    @DisplayName("Scenario 1: Payment processing failure releases seats to AVAILABLE and marks booking FAILED")
    void testPaymentProcessingFailureReleasesSeats() {
        // 1. Hold seats
        HoldBookingRequest holdReq = new HoldBookingRequest(journey.getId(), List.of(seat1.getId(), seat2.getId()), primaryUser.getId());
        BookingResponse holdResp = bookingService.holdSeats(primaryUser.getId(), holdReq, "hold-key-1", "{}");
        UUID bookingId = holdResp.id();

        // 2. Initiate confirmation -> transitions to PAYMENT_PENDING
        BookingResponse pendingResp = bookingService.confirmBooking(bookingId, primaryUser.getId());
        assertThat(pendingResp.status()).isEqualTo(BookingStatus.PAYMENT_PENDING);

        // Verify seats are held
        SeatInventory heldInv1 = seatInventoryRepository.findById(inventory1.getId()).orElseThrow();
        SeatInventory heldInv2 = seatInventoryRepository.findById(inventory2.getId()).orElseThrow();
        assertThat(heldInv1.getStatus()).isEqualTo(SeatStatus.HELD);
        assertThat(heldInv2.getStatus()).isEqualTo(SeatStatus.HELD);

        // 3. Simulate payment processing failure event from payment gateway
        UUID paymentEventId = UUID.randomUUID();
        BookingResponse failResp = bookingService.processPaymentResult(bookingId, PaymentStatus.FAILED, paymentEventId);

        // 4. Invariant assertions
        assertThat(failResp.status()).isEqualTo(BookingStatus.FAILED);

        Booking booking = bookingRepository.findById(bookingId).orElseThrow();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.FAILED);

        Payment payment = paymentRepository.findByBookingId(bookingId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);

        // Invariant 4: Non-held seats must have null hold metadata
        SeatInventory updatedInv1 = seatInventoryRepository.findById(inventory1.getId()).orElseThrow();
        SeatInventory updatedInv2 = seatInventoryRepository.findById(inventory2.getId()).orElseThrow();

        assertThat(updatedInv1.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(updatedInv1.getHeldBy()).isNull();
        assertThat(updatedInv1.getHeldUntil()).isNull();

        assertThat(updatedInv2.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(updatedInv2.getHeldBy()).isNull();
        assertThat(updatedInv2.getHeldUntil()).isNull();

        // Outbox event verification
        List<OutboxEvent> outboxEvents = outboxEventRepository.findAll();
        assertThat(outboxEvents).anyMatch(e -> "PaymentFailed".equals(e.getEventType()));
        assertThat(outboxEvents).anyMatch(e -> "SeatReleased".equals(e.getEventType()));

        // Invariant: Another user can immediately hold and confirm the released seats without conflict
        HoldBookingRequest secReq = new HoldBookingRequest(journey.getId(), List.of(seat1.getId()), secondaryUser.getId());
        BookingResponse secHold = bookingService.holdSeats(secondaryUser.getId(), secReq, "hold-key-sec", "{}");
        assertThat(secHold.status()).isEqualTo(BookingStatus.HELD);
    }

    @Test
    @DisplayName("Scenario 2: Abandoned payment timeout is expired by reconciliation and releases seats")
    void testAbandonedPaymentTimeoutReconciliation() {
        // 1. User holds seat and transitions to PAYMENT_PENDING
        HoldBookingRequest holdReq = new HoldBookingRequest(journey.getId(), List.of(seat1.getId()), primaryUser.getId());
        BookingResponse holdResp = bookingService.holdSeats(primaryUser.getId(), holdReq, "hold-key-abandon", "{}");
        UUID bookingId = holdResp.id();

        bookingService.confirmBooking(bookingId, primaryUser.getId());

        Booking booking = bookingRepository.findById(bookingId).orElseThrow();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PAYMENT_PENDING);

        // 2. Simulate payment deadline passed (e.g., user abandoned payment page 10 minutes ago)
        booking.setPaymentInitiatedAt(Instant.now().minus(Duration.ofMinutes(10)));
        bookingRepository.save(booking);

        // 3. Trigger reconciliation run
        ReservationReconciliationService.ReconciliationReport report = reconciliationService.runReconciliation();
        assertThat(report.expiredPaymentsCount()).isGreaterThanOrEqualTo(1);

        // 4. Verify booking transitioned to EXPIRED
        Booking expiredBooking = bookingRepository.findById(bookingId).orElseThrow();
        assertThat(expiredBooking.getStatus()).isEqualTo(BookingStatus.EXPIRED);

        // 5. Verify seat is released back to AVAILABLE
        SeatInventory releasedSeat = seatInventoryRepository.findById(inventory1.getId()).orElseThrow();
        assertThat(releasedSeat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(releasedSeat.getHeldBy()).isNull();
        assertThat(releasedSeat.getHeldUntil()).isNull();

        // Outbox contains BookingExpired and SeatReleased
        List<OutboxEvent> outboxEvents = outboxEventRepository.findAll();
        assertThat(outboxEvents).anyMatch(e -> "BookingExpired".equals(e.getEventType()));
        assertThat(outboxEvents).anyMatch(e -> "SeatReleased".equals(e.getEventType()));
    }

    @Test
    @DisplayName("Scenario 3: Late payment arrives after expiry — emits RefundRequired and booking remains EXPIRED (Invariant 9)")
    void testLatePaymentAfterExpiryTriggersRefund() {
        // 1. Hold and confirm booking
        HoldBookingRequest holdReq = new HoldBookingRequest(journey.getId(), List.of(seat1.getId()), primaryUser.getId());
        BookingResponse holdResp = bookingService.holdSeats(primaryUser.getId(), holdReq, "hold-key-late", "{}");
        UUID bookingId = holdResp.id();

        bookingService.confirmBooking(bookingId, primaryUser.getId());

        // 2. Expire the booking via reconciliation
        Booking booking = bookingRepository.findById(bookingId).orElseThrow();
        booking.setPaymentInitiatedAt(Instant.now().minus(Duration.ofMinutes(10)));
        bookingRepository.save(booking);
        reconciliationService.runReconciliation();

        Booking expiredBooking = bookingRepository.findById(bookingId).orElseThrow();
        assertThat(expiredBooking.getStatus()).isEqualTo(BookingStatus.EXPIRED);

        // In the meantime, another user takes the released seat!
        HoldBookingRequest secReq = new HoldBookingRequest(journey.getId(), List.of(seat1.getId()), secondaryUser.getId());
        bookingService.holdSeats(secondaryUser.getId(), secReq, "sec-hold-key", "{}");

        // 3. Late payment success event arrives from bank gateway
        UUID latePaymentEventId = UUID.randomUUID();
        bookingService.processPaymentResult(bookingId, PaymentStatus.SUCCESS, latePaymentEventId);

        // 4. Critical Invariant 9 assertions:
        // Booking must NEVER resurrect to CONFIRMED
        Booking finalBooking = bookingRepository.findById(bookingId).orElseThrow();
        assertThat(finalBooking.getStatus()).isEqualTo(BookingStatus.EXPIRED);

        // Payment status must transition to REFUND_PENDING
        Payment payment = paymentRepository.findByBookingId(bookingId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUND_PENDING);

        // Outbox must contain RefundRequired event
        List<OutboxEvent> outboxEvents = outboxEventRepository.findAll();
        assertThat(outboxEvents).anyMatch(e ->
            "RefundRequired".equals(e.getEventType()) &&
            e.getPayload().contains("PAYMENT_ARRIVED_AFTER_EXPIRY")
        );

        // The seat still belongs to secondaryUser's hold, not primaryUser!
        SeatInventory seatStatus = seatInventoryRepository.findById(inventory1.getId()).orElseThrow();
        assertThat(seatStatus.getStatus()).isEqualTo(SeatStatus.HELD);
        assertThat(seatStatus.getHeldBy()).isEqualTo(secondaryUser.getId());
    }

    @Test
    @DisplayName("Scenario 4: Duplicate payment webhook is idempotent and deduplicated via ProcessedEvents")
    void testDuplicatePaymentWebhookIdempotency() {
        // 1. Hold and confirm booking
        HoldBookingRequest holdReq = new HoldBookingRequest(journey.getId(), List.of(seat1.getId()), primaryUser.getId());
        BookingResponse holdResp = bookingService.holdSeats(primaryUser.getId(), holdReq, "hold-key-idem", "{}");
        UUID bookingId = holdResp.id();

        bookingService.confirmBooking(bookingId, primaryUser.getId());

        UUID paymentEventId = UUID.randomUUID();

        // 2. First delivery of payment result
        BookingResponse firstResp = bookingService.processPaymentResult(bookingId, PaymentStatus.SUCCESS, paymentEventId);
        assertThat(firstResp.status()).isEqualTo(BookingStatus.CONFIRMED);

        long confirmedEventCount = outboxEventRepository.findAll().stream()
            .filter(e -> "BookingConfirmed".equals(e.getEventType()))
            .count();
        assertThat(confirmedEventCount).isEqualTo(1);

        // 3. Second delivery of same webhook event (duplicate Kafka delivery / network retry)
        BookingResponse duplicateResp = bookingService.processPaymentResult(bookingId, PaymentStatus.SUCCESS, paymentEventId);
        assertThat(duplicateResp.status()).isEqualTo(BookingStatus.CONFIRMED);

        // Verify outbox was NOT duplicated
        long confirmedEventCountAfterDup = outboxEventRepository.findAll().stream()
            .filter(e -> "BookingConfirmed".equals(e.getEventType()))
            .count();
        assertThat(confirmedEventCountAfterDup).isEqualTo(1);

        // Processed event record exists
        assertThat(processedEventRepository.count()).isEqualTo(1);
    }
}

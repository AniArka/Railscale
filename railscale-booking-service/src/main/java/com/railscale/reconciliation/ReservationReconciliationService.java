package com.railscale.reconciliation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.railscale.booking.domain.Booking;
import com.railscale.booking.domain.BookingItem;
import com.railscale.booking.domain.BookingStatus;
import com.railscale.booking.repository.BookingItemRepository;
import com.railscale.booking.repository.BookingRepository;
import com.railscale.inventory.domain.SeatInventory;
import com.railscale.inventory.domain.SeatStatus;
import com.railscale.inventory.repository.SeatInventoryRepository;
import com.railscale.outbox.domain.OutboxEvent;
import com.railscale.outbox.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ReservationReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationReconciliationService.class);

    private final SeatInventoryRepository seatInventoryRepository;
    private final BookingRepository bookingRepository;
    private final BookingItemRepository bookingItemRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final Duration paymentTimeout;
    private com.railscale.metrics.BookingMetrics bookingMetrics;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setBookingMetrics(com.railscale.metrics.BookingMetrics bookingMetrics) {
        this.bookingMetrics = bookingMetrics;
    }

    public ReservationReconciliationService(
        SeatInventoryRepository seatInventoryRepository,
        BookingRepository bookingRepository,
        BookingItemRepository bookingItemRepository,
        OutboxEventRepository outboxEventRepository,
        ObjectMapper objectMapper,
        @Value("${railscale.reservation.payment-timeout:2m}") Duration paymentTimeout
    ) {
        this.seatInventoryRepository = seatInventoryRepository;
        this.bookingRepository = bookingRepository;
        this.bookingItemRepository = bookingItemRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.paymentTimeout = paymentTimeout;
    }

    public record ReconciliationReport(int expiredHoldsCount, int expiredPaymentsCount) {}

    @Scheduled(fixedDelayString = "${railscale.reconciliation.interval-ms:5000}")
    @Transactional
    public void scheduledReconciliation() {
        try {
            ReconciliationReport report = runReconciliation();
            if (report.expiredHoldsCount() > 0 || report.expiredPaymentsCount() > 0) {
                log.info("Reconciliation complete: {} expired holds, {} expired payments processed.",
                    report.expiredHoldsCount(), report.expiredPaymentsCount());
            }
        } catch (Exception e) {
            log.error("Error occurred during scheduled reconciliation", e);
        }
    }

    @Transactional
    public ReconciliationReport runReconciliation() {
        int expiredHolds = expireHolds();
        int expiredPayments = expirePaymentPendingBookings();
        return new ReconciliationReport(expiredHolds, expiredPayments);
    }

    /**
     * Section 23.1 & Section 26: Expire seat holds whose lease deadline has passed.
     */
    @Transactional
    public int expireHolds() {
        Instant now = Instant.now();
        List<SeatInventory> expiredSeats = seatInventoryRepository.findExpiredHoldsForUpdate(SeatStatus.HELD, now);

        if (expiredSeats.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (SeatInventory seat : expiredSeats) {
            // Re-verify that hold has expired
            if (seat.getHeldUntil() != null && seat.getHeldUntil().isBefore(now)) {
                // If associated with a HELD booking, transition booking to EXPIRED
                List<BookingItem> items = bookingItemRepository.findAll().stream()
                    .filter(bi -> bi.getInventory().getId().equals(seat.getId()))
                    .toList();

                for (BookingItem item : items) {
                    Booking booking = item.getBooking();
                    if (booking != null && booking.getStatus() == BookingStatus.HELD) {
                        booking.setStatus(BookingStatus.EXPIRED);
                        bookingRepository.save(booking);
                    }
                }

                // Invariant 4: Non-held seats must have null hold metadata
                seat.setStatus(SeatStatus.AVAILABLE);
                seat.setHeldBy(null);
                seat.setHeldUntil(null);
                seat.setVersion(seat.getVersion() + 1);
                seatInventoryRepository.save(seat);

                outboxEventRepository.save(new OutboxEvent(
                    "Inventory",
                    seat.getId(),
                    "SeatReleased",
                    toJson(Map.of("inventoryId", seat.getId(), "reason", "HOLD_EXPIRED"))
                ));
                count++;
            }
        }
        if (bookingMetrics != null && count > 0) {
            bookingMetrics.incrementSeatHoldExpired(count);
        }
        return count;
    }

    /**
     * Section 23.2 & Section 26: Expire PAYMENT_PENDING bookings whose payment deadline has passed.
     * Atomically transitions booking -> EXPIRED and seats -> AVAILABLE.
     */
    @Transactional
    public int expirePaymentPendingBookings() {
        Instant now = Instant.now();
        Instant cutoff = now.minus(paymentTimeout);

        List<Booking> expiredBookings = bookingRepository.findExpiredPaymentPendingForUpdate(
            BookingStatus.PAYMENT_PENDING,
            cutoff
        );

        if (expiredBookings.isEmpty()) {
            return 0;
        }

        for (Booking booking : expiredBookings) {
            // Invariant: payment deadline exceeded -> atomically expire booking and release inventory
            booking.setStatus(BookingStatus.EXPIRED);
            bookingRepository.save(booking);

            List<SeatInventory> seats = seatInventoryRepository.findByBookingIdForUpdate(booking.getId());
            for (SeatInventory seat : seats) {
                seat.setStatus(SeatStatus.AVAILABLE);
                seat.setHeldBy(null);
                seat.setHeldUntil(null);
                seat.setVersion(seat.getVersion() + 1);
                seatInventoryRepository.save(seat);

                outboxEventRepository.save(new OutboxEvent(
                    "Inventory",
                    seat.getId(),
                    "SeatReleased",
                    toJson(Map.of("bookingId", booking.getId(), "inventoryId", seat.getId(), "reason", "PAYMENT_TIMEOUT"))
                ));
            }

            outboxEventRepository.save(new OutboxEvent(
                "Booking",
                booking.getId(),
                "BookingExpired",
                toJson(Map.of("bookingId", booking.getId(), "reason", "PAYMENT_TIMEOUT"))
            ));
        }

        return expiredBookings.size();
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize to JSON", e);
        }
    }
}

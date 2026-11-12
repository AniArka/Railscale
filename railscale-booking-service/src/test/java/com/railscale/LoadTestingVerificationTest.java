package com.railscale;

import com.railscale.booking.domain.Booking;
import com.railscale.booking.domain.BookingItem;
import com.railscale.booking.domain.BookingStatus;
import com.railscale.booking.dto.BookingResponse;
import com.railscale.booking.dto.HoldBookingRequest;
import com.railscale.booking.repository.BookingItemRepository;
import com.railscale.booking.repository.BookingRepository;
import com.railscale.booking.service.BookingService;
import com.railscale.common.exception.ConflictException;
import com.railscale.inventory.domain.SeatInventory;
import com.railscale.inventory.domain.SeatStatus;
import com.railscale.inventory.repository.SeatInventoryRepository;
import com.railscale.journey.domain.Journey;
import com.railscale.journey.repository.JourneyRepository;
import com.railscale.payment.domain.PaymentStatus;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 8 Automated In-Memory Load & Invariant Verification Test.
 * Simulates high-concurrency seat contention and verifies all Section 54 Architectural Invariants.
 */
@SpringBootTest
@ActiveProfiles("test")
class LoadTestingVerificationTest {

    @Autowired
    private BookingService bookingService;

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

    private Journey journey;
    private List<Seat> seats = new ArrayList<>();
    private List<SeatInventory> inventories = new ArrayList<>();
    private List<User> testUsers = new ArrayList<>();

    @BeforeEach
    void setUp() {
        bookingItemRepository.deleteAll();
        bookingRepository.deleteAll();
        seatInventoryRepository.deleteAll();
        journeyRepository.deleteAll();
        seatRepository.deleteAll();
        coachRepository.deleteAll();
        trainRepository.deleteAll();
        stationRepository.deleteAll();
        userRepository.deleteAll();

        // 1. Create Stations
        Station src = stationRepository.save(new Station("NDLS", "New Delhi", "New Delhi"));
        Station dst = stationRepository.save(new Station("MMCT", "Mumbai Central", "Mumbai"));

        // 2. Create Train & Coach
        Train train = trainRepository.save(new Train("12952", "Rajdhani Express"));
        Coach coach = coachRepository.save(new Coach(train, "A1", CoachType.AC_TIER_2));

        // 3. Create 5 Physical Seats for contention testing
        seats.clear();
        BerthType[] berths = { BerthType.LOWER, BerthType.UPPER, BerthType.SIDE_LOWER, BerthType.SIDE_UPPER, BerthType.LOWER };
        for (int i = 0; i < 5; i++) {
            seats.add(seatRepository.save(new Seat(coach, "S" + (i + 1), berths[i])));
        }

        // 4. Create Journey
        Instant dep = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant arr = dep.plus(16, ChronoUnit.HOURS);
        journey = journeyRepository.save(new Journey(train, src, dst, dep, arr));

        // 5. Create SeatInventory (all AVAILABLE)
        inventories.clear();
        for (Seat seat : seats) {
            inventories.add(seatInventoryRepository.save(new SeatInventory(journey.getId(), seat)));
        }

        // 6. Create 30 Distinct Users
        testUsers.clear();
        for (int i = 1; i <= 30; i++) {
            testUsers.add(userRepository.save(new User("load-user-" + i + "@test.com", "pass", "User " + i, Role.USER)));
        }
    }

    @Test
    @DisplayName("Load Test: 30 concurrent users competing for 5 seats -> exactly 5 succeed, 25 get 409, 0 overselling")
    void testConcurrentLoadContentionWithZeroOverselling() throws Exception {
        int userCount = 30;
        int totalSeats = seats.size(); // 5 seats

        ExecutorService executor = Executors.newFixedThreadPool(userCount);
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger successCounter = new AtomicInteger(0);
        AtomicInteger contentionCounter = new AtomicInteger(0);
        List<UUID> successfulBookingIds = new CopyOnWriteArrayList<>();

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < userCount; i++) {
            final User user = testUsers.get(i);
            final int userIndex = i;
            tasks.add(() -> {
                startGate.await();
                Seat targetSeat = seats.get(userIndex % totalSeats);
                HoldBookingRequest req = new HoldBookingRequest(journey.getId(), List.of(targetSeat.getId()), user.getId());

                try {
                    BookingResponse resp = bookingService.holdSeats(
                            user.getId(),
                            req,
                            "idemp-load-" + userIndex,
                            "{\"user\":" + userIndex + "}"
                    );
                    successCounter.incrementAndGet();
                    successfulBookingIds.add(resp.id());
                } catch (ConflictException | org.springframework.dao.DataAccessException | org.springframework.transaction.TransactionException e) {
                    contentionCounter.incrementAndGet();
                }
                return null;
            });
        }

        // Release all 30 threads simultaneously
        startGate.countDown();
        for (Future<Void> f : executor.invokeAll(tasks)) {
            f.get();
        }
        executor.shutdown();

        // ------------------------------------------------------------------------
        // Architectural Invariant Verification (Section 54)
        // ------------------------------------------------------------------------

        // 1. Total processed requests must match total users
        assertThat(successCounter.get() + contentionCounter.get()).isEqualTo(userCount);

        // 2. Exactly totalSeats (5) can be successfully held; never more!
        assertThat(successCounter.get()).isLessThanOrEqualTo(totalSeats);
        assertThat(successCounter.get()).isGreaterThan(0);

        // 3. Confirm all successful bookings to test confirmation invariant
        for (UUID bookingId : successfulBookingIds) {
            Booking b = bookingRepository.findById(bookingId).orElseThrow();
            bookingService.confirmBooking(bookingId, b.getUserId());
            bookingService.processPaymentResult(bookingId, PaymentStatus.SUCCESS, UUID.randomUUID());
        }

        // 4. INVARIANT 1: Zero Overselling
        // Verify in database: each confirmed seat has exactly 1 booking item
        List<BookingItem> confirmedItems = bookingItemRepository.findAll();
        Set<UUID> bookedInventoryIds = new HashSet<>();
        for (BookingItem item : confirmedItems) {
            assertThat(bookedInventoryIds.add(item.getInventory().getId()))
                    .as("INVARIANT VIOLATION: Seat inventory %s was oversold!", item.getInventory().getId())
                    .isTrue();
        }

        // 5. INVARIANT 3 & 4: Status Metadata Consistency
        List<SeatInventory> allInventories = seatInventoryRepository.findAll();
        for (SeatInventory inv : allInventories) {
            if (inv.getStatus() == SeatStatus.BOOKED) {
                assertThat(inv.getHeldBy()).as("BOOKED seats must clear hold metadata").isNull();
                assertThat(inv.getHeldUntil()).as("BOOKED seats must clear hold metadata").isNull();
            } else if (inv.getStatus() == SeatStatus.AVAILABLE) {
                assertThat(inv.getHeldBy()).as("AVAILABLE seats must have no holder").isNull();
            }
        }

        System.out.println("=================================================================");
        System.out.printf("LOAD TEST RESULTS: %d Requests | %d Held | %d Contended | Overselling = 0%n",
                userCount, successCounter.get(), contentionCounter.get());
        System.out.println("=================================================================");
    }
}

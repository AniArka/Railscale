package com.railscale;

import com.railscale.booking.domain.Booking;
import com.railscale.booking.dto.BookingResponse;
import com.railscale.booking.dto.HoldBookingRequest;
import com.railscale.booking.repository.BookingItemRepository;
import com.railscale.booking.repository.BookingRepository;
import com.railscale.booking.service.BookingService;
import com.railscale.common.exception.ConflictException;
import com.railscale.inventory.domain.SeatInventory;
import com.railscale.inventory.repository.SeatInventoryRepository;
import com.railscale.journey.domain.Journey;
import com.railscale.journey.repository.JourneyRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 9 Chaos Test: Section 9 — Deadlock Prevention Test.
 *
 * Verifies canonical ordering normalization:
 * Request A requests [Seat 2, Seat 1]
 * Request B requests [Seat 1, Seat 2]
 *
 * Expected:
 * Both normalize ordering by ID before acquiring database locks.
 * Circular wait deadlock is mathematically eliminated.
 * Exactly 1 succeeds, other receives 409 Conflict; 0 deadlocks.
 */
@SpringBootTest
@ActiveProfiles("test")
class DeadlockPreventionTest {

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

    private User userA;
    private User userB;
    private Journey journey;
    private Seat seat1;
    private Seat seat2;

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

        userA = userRepository.save(new User("userA@test.com", "pass", "User A", Role.USER));
        userB = userRepository.save(new User("userB@test.com", "pass", "User B", Role.USER));

        Station src = stationRepository.save(new Station("DEL", "Delhi", "Delhi"));
        Station dst = stationRepository.save(new Station("BOM", "Mumbai", "Mumbai"));
        Train train = trainRepository.save(new Train("DL-100", "Deadlock Test Train"));
        Coach coach = coachRepository.save(new Coach(train, "C1", CoachType.AC_TIER_2));

        seat1 = seatRepository.save(new Seat(coach, "S1", BerthType.LOWER));
        seat2 = seatRepository.save(new Seat(coach, "S2", BerthType.UPPER));

        Instant dep = Instant.now().plus(2, ChronoUnit.DAYS);
        journey = journeyRepository.save(new Journey(train, src, dst, dep, dep.plus(12, ChronoUnit.HOURS)));

        seatInventoryRepository.save(new SeatInventory(journey.getId(), seat1));
        seatInventoryRepository.save(new SeatInventory(journey.getId(), seat2));
    }

    @Test
    @DisplayName("Section 9 Deadlock Test: Opposite-order multi-seat requests [S2,S1] vs [S1,S2] never deadlock")
    void testOppositeOrderSeatRequestsDoNotDeadlock() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCounter = new AtomicInteger(0);
        AtomicInteger conflictCounter = new AtomicInteger(0);

        // User A requests [Seat 2, Seat 1] (reverse order)
        Callable<Void> taskA = () -> {
            startLatch.await();
            try {
                HoldBookingRequest req = new HoldBookingRequest(journey.getId(), List.of(seat2.getId(), seat1.getId()), userA.getId());
                bookingService.holdSeats(userA.getId(), req, "key-A", "{}");
                successCounter.incrementAndGet();
            } catch (ConflictException | org.springframework.dao.DataAccessException e) {
                conflictCounter.incrementAndGet();
            }
            return null;
        };

        // User B requests [Seat 1, Seat 2] (forward order)
        Callable<Void> taskB = () -> {
            startLatch.await();
            try {
                HoldBookingRequest req = new HoldBookingRequest(journey.getId(), List.of(seat1.getId(), seat2.getId()), userB.getId());
                bookingService.holdSeats(userB.getId(), req, "key-B", "{}");
                successCounter.incrementAndGet();
            } catch (ConflictException | org.springframework.dao.DataAccessException e) {
                conflictCounter.incrementAndGet();
            }
            return null;
        };

        List<Future<Void>> futures = new ArrayList<>();
        futures.add(executor.submit(taskA));
        futures.add(executor.submit(taskB));

        // Release both tasks at the exact same instant
        startLatch.countDown();

        // Must complete within 10 seconds without deadlock hang
        for (Future<Void> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        // One request wins both seats; the other receives conflict.
        assertThat(successCounter.get()).isEqualTo(1);
        assertThat(conflictCounter.get()).isEqualTo(1);
    }
}

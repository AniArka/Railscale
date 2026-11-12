package com.railscale;

import com.railscale.booking.dto.HoldBookingRequest;
import com.railscale.booking.service.BookingService;
import com.railscale.common.exception.ConflictException;
import com.railscale.inventory.domain.SeatInventory;
import com.railscale.inventory.repository.SeatInventoryRepository;
import com.railscale.journey.domain.Journey;
import com.railscale.journey.repository.JourneyRepository;
import com.railscale.metrics.BookingMetrics;
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
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = RailScaleApplication.class)
@ActiveProfiles("test")
class BookingMetricsTest {

    @Autowired
    private BookingMetrics bookingMetrics;

    @Autowired(required = false)
    private MeterRegistry meterRegistry;

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

    private User testUser;
    private Journey testJourney;
    private SeatInventory testInventory;

    @Autowired
    private com.railscale.idempotency.repository.IdempotencyKeyRepository idempotencyKeyRepository;

    @Autowired
    private com.railscale.outbox.repository.OutboxEventRepository outboxEventRepository;

    @Autowired
    private com.railscale.booking.repository.BookingRepository bookingRepository;

    @BeforeEach
    void setUp() {
        if (meterRegistry == null) {
            meterRegistry = bookingMetrics.getRegistry();
        }

        idempotencyKeyRepository.deleteAll();
        outboxEventRepository.deleteAll();
        bookingRepository.deleteAll();
        seatInventoryRepository.deleteAll();
        journeyRepository.deleteAll();
        seatRepository.deleteAll();
        coachRepository.deleteAll();
        trainRepository.deleteAll();
        stationRepository.deleteAll();
        userRepository.deleteAll();
        testUser = userRepository.save(new User("metrics@railscale.com", "hash", "Metrics User", Role.USER));

        Station src = stationRepository.save(new Station("MTR-SRC", "Metrics Source", "CityA"));
        Station dst = stationRepository.save(new Station("MTR-DST", "Metrics Dest", "CityB"));

        Train train = trainRepository.save(new Train("MTR-101", "Metrics Express"));
        Coach coach = coachRepository.save(new Coach(train, "M-A1", CoachType.AC_TIER_3));
        Seat seat = seatRepository.save(new Seat(coach, "1", BerthType.LOWER));

        Instant departure = Instant.now().plus(2, ChronoUnit.DAYS);
        testJourney = journeyRepository.save(new Journey(train, src, dst, departure, departure.plus(12, ChronoUnit.HOURS)));
        testInventory = seatInventoryRepository.save(new SeatInventory(testJourney.getId(), seat));
    }

    @Test
    @DisplayName("Should increment booking_requests_total and seat_hold_total upon successful hold")
    void testHoldMetricsIncremented() {
        double beforeRequests = meterRegistry.get("booking_requests_total").counter().count();
        double beforeSeatHolds = meterRegistry.get("seat_hold_total").counter().count();

        bookingService.holdSeats(
                testUser.getId(),
                new HoldBookingRequest(testJourney.getId(), List.of(testInventory.getId()), testUser.getId()),
                "METRICS-KEY-" + UUID.randomUUID(),
                "{}"
        );

        double afterRequests = meterRegistry.get("booking_requests_total").counter().count();
        double afterSeatHolds = meterRegistry.get("seat_hold_total").counter().count();

        assertThat(afterRequests).isEqualTo(beforeRequests + 1);
        assertThat(afterSeatHolds).isEqualTo(beforeSeatHolds + 1);
    }

    @Test
    @DisplayName("Should increment seat_contention_total when hold conflict occurs")
    void testContentionMetricIncremented() {
        // First hold succeeds
        bookingService.holdSeats(
                testUser.getId(),
                new HoldBookingRequest(testJourney.getId(), List.of(testInventory.getId()), testUser.getId()),
                "METRICS-KEY-1-" + UUID.randomUUID(),
                "{}"
        );

        double beforeContention = meterRegistry.get("seat_contention_total").counter().count();

        // Second hold for the same seat must fail with ConflictException
        assertThatThrownBy(() ->
                bookingService.holdSeats(
                        testUser.getId(),
                        new HoldBookingRequest(testJourney.getId(), List.of(testInventory.getId()), testUser.getId()),
                        "METRICS-KEY-2-" + UUID.randomUUID(),
                        "{}"
                )
        ).isInstanceOf(ConflictException.class);

        double afterContention = meterRegistry.get("seat_contention_total").counter().count();
        assertThat(afterContention).isEqualTo(beforeContention + 1);
    }

    @Test
    @DisplayName("Should track active_holds and confirmed_bookings dynamic gauges")
    void testGaugesRegistered() {
        assertThat(meterRegistry.find("active_holds").gauge()).isNotNull();
        assertThat(meterRegistry.find("confirmed_bookings").gauge()).isNotNull();
    }
}

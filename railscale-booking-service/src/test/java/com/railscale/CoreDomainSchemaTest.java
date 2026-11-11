package com.railscale;

import com.railscale.inventory.domain.SeatInventory;
import com.railscale.inventory.domain.SeatStatus;
import com.railscale.inventory.repository.SeatInventoryRepository;
import com.railscale.journey.domain.Journey;
import com.railscale.journey.domain.JourneyStatus;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CoreDomainSchemaTest {

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

    private Station sourceStation;
    private Station destStation;
    private Train train;
    private Coach coach;
    private Seat seat;
    private Journey journey;

    @BeforeEach
    void setUp() {
        sourceStation = stationRepository.save(new Station("HWH", "Howrah Junction", "Kolkata"));
        destStation = stationRepository.save(new Station("NDLS", "New Delhi", "Delhi"));

        train = trainRepository.save(new Train("RS-100", "RailScale Superfast"));

        coach = new Coach(train, "B1", CoachType.AC_TIER_3);
        coach = coachRepository.save(coach);

        seat = new Seat(coach, "1", BerthType.LOWER);
        seat = seatRepository.save(seat);

        Instant now = Instant.now().plus(1, ChronoUnit.DAYS);
        journey = journeyRepository.save(new Journey(train, sourceStation, destStation, now, now.plus(12, ChronoUnit.HOURS)));
    }

    @Test
    @DisplayName("Invariant 2: Database constraint prevents duplicate seat inventory for same (journey_id, seat_id)")
    void duplicateSeatInventoryForSameJourneyAndSeatMustFail() {
        // First inventory record succeeds
        SeatInventory inv1 = new SeatInventory(journey.getId(), seat);
        seatInventoryRepository.saveAndFlush(inv1);

        assertThat(inv1.getId()).isNotNull();
        assertThat(inv1.getStatus()).isEqualTo(SeatStatus.AVAILABLE);

        // Attempt to insert duplicate record representing the same physical seat for the same journey
        SeatInventory inv2 = new SeatInventory(journey.getId(), seat);

        assertThatThrownBy(() -> seatInventoryRepository.saveAndFlush(inv2))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Verify foreign key relations and status lifecycle in seat_inventory")
    void verifySeatInventoryRelationsAndStatus() {
        SeatInventory inv = new SeatInventory(journey.getId(), seat);
        inv.setStatus(SeatStatus.HELD);
        UUID userHoldId = UUID.randomUUID();
        inv.setHeldBy(userHoldId);
        inv.setHeldUntil(Instant.now().plus(5, ChronoUnit.MINUTES));

        SeatInventory saved = seatInventoryRepository.saveAndFlush(inv);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getHeldBy()).isEqualTo(userHoldId);
        assertThat(saved.getStatus()).isEqualTo(SeatStatus.HELD);
        assertThat(saved.getVersion()).isZero();
    }
}

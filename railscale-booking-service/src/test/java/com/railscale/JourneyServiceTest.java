package com.railscale;

import com.railscale.common.exception.BadRequestException;
import com.railscale.inventory.domain.SeatStatus;
import com.railscale.inventory.web.dto.SeatInventoryResponse;
import com.railscale.journey.domain.JourneyStatus;
import com.railscale.journey.service.JourneyService;
import com.railscale.journey.web.dto.CreateJourneyRequest;
import com.railscale.journey.web.dto.JourneyResponse;
import com.railscale.train.domain.CoachType;
import com.railscale.train.service.TrainAdminService;
import com.railscale.train.web.dto.CreateStationRequest;
import com.railscale.train.web.dto.CreateTrainRequest;
import com.railscale.train.web.dto.StationResponse;
import com.railscale.train.web.dto.TrainResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class JourneyServiceTest {

    @Autowired
    private JourneyService journeyService;

    @Autowired
    private TrainAdminService trainAdminService;

    private StationResponse howrah;
    private StationResponse newDelhi;
    private TrainResponse train;

    @BeforeEach
    void setUp() {
        howrah = trainAdminService.createStation(new CreateStationRequest("HWH", "Howrah Junction", "Kolkata"));
        newDelhi = trainAdminService.createStation(new CreateStationRequest("NDLS", "New Delhi", "Delhi"));

        CreateTrainRequest trainRequest = new CreateTrainRequest(
            "RS-200",
            "Rajdhani Express",
            List.of(
                new CreateTrainRequest.CreateCoachRequest("B1", CoachType.AC_TIER_3, 10),
                new CreateTrainRequest.CreateCoachRequest("A1", CoachType.AC_TIER_2, 10)
            )
        );
        train = trainAdminService.createTrain(trainRequest);
    }

    @Test
    @DisplayName("Scheduling journey automatically generates seat inventory for all physical seats")
    void scheduleJourneyGeneratesInventory() {
        Instant departure = Instant.now().plus(2, ChronoUnit.DAYS);
        Instant arrival = departure.plus(16, ChronoUnit.HOURS);

        CreateJourneyRequest request = new CreateJourneyRequest(
            train.id(),
            howrah.id(),
            newDelhi.id(),
            departure,
            arrival
        );

        JourneyResponse response = journeyService.scheduleJourney(request);

        assertThat(response.id()).isNotNull();
        assertThat(response.trainNumber()).isEqualTo("RS-200");
        assertThat(response.sourceStationCode()).isEqualTo("HWH");
        assertThat(response.destinationStationCode()).isEqualTo("NDLS");
        assertThat(response.status()).isEqualTo(JourneyStatus.SCHEDULED);
        assertThat(response.availableSeats()).isEqualTo(20);

        List<SeatInventoryResponse> seats = journeyService.getSeatsForJourney(response.id());
        assertThat(seats).hasSize(20);
        assertThat(seats).allMatch(s -> s.status() == SeatStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Search journey by source, destination and date")
    void searchJourneysByDateAndRoute() {
        LocalDate travelDate = LocalDate.now().plusDays(3);
        Instant departure = travelDate.atTime(10, 0).toInstant(ZoneOffset.UTC);
        Instant arrival = departure.plus(12, ChronoUnit.HOURS);

        journeyService.scheduleJourney(new CreateJourneyRequest(
            train.id(),
            howrah.id(),
            newDelhi.id(),
            departure,
            arrival
        ));

        List<JourneyResponse> searchResults = journeyService.searchJourneys(
            howrah.id(),
            newDelhi.id(),
            travelDate
        );

        assertThat(searchResults).hasSize(1);
        assertThat(searchResults.get(0).availableSeats()).isEqualTo(20);

        // Different date returns empty
        List<JourneyResponse> emptyResults = journeyService.searchJourneys(
            howrah.id(),
            newDelhi.id(),
            travelDate.plusDays(5)
        );
        assertThat(emptyResults).isEmpty();
    }

    @Test
    @DisplayName("Reject journey where arrival is before departure or stations are identical")
    void validateJourneyTimestampsAndStations() {
        Instant departure = Instant.now().plus(2, ChronoUnit.DAYS);
        Instant invalidArrival = departure.minus(2, ChronoUnit.HOURS);

        assertThatThrownBy(() -> journeyService.scheduleJourney(new CreateJourneyRequest(
            train.id(),
            howrah.id(),
            newDelhi.id(),
            departure,
            invalidArrival
        ))).isInstanceOf(BadRequestException.class);

        assertThatThrownBy(() -> journeyService.scheduleJourney(new CreateJourneyRequest(
            train.id(),
            howrah.id(),
            howrah.id(),
            departure,
            departure.plus(5, ChronoUnit.HOURS)
        ))).isInstanceOf(BadRequestException.class);
    }
}

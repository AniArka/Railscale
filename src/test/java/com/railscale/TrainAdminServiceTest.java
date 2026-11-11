package com.railscale;

import com.railscale.common.exception.ConflictException;
import com.railscale.train.domain.BerthType;
import com.railscale.train.domain.Coach;
import com.railscale.train.domain.CoachType;
import com.railscale.train.domain.Seat;
import com.railscale.train.domain.Train;
import com.railscale.train.repository.TrainRepository;
import com.railscale.train.service.TrainAdminService;
import com.railscale.train.web.dto.CreateStationRequest;
import com.railscale.train.web.dto.CreateTrainRequest;
import com.railscale.train.web.dto.StationResponse;
import com.railscale.train.web.dto.TrainResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TrainAdminServiceTest {

    @Autowired
    private TrainAdminService trainAdminService;

    @Autowired
    private TrainRepository trainRepository;

    @Test
    @DisplayName("Create station successfully and reject duplicate station code")
    void createStationAndRejectDuplicates() {
        StationResponse station = trainAdminService.createStation(new CreateStationRequest("GAYA", "Gaya Junction", "Gaya"));
        assertThat(station.id()).isNotNull();
        assertThat(station.code()).isEqualTo("GAYA");

        assertThatThrownBy(() -> trainAdminService.createStation(new CreateStationRequest("GAYA", "Duplicate Gaya", "Gaya")))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("Create train with coaches and auto-generate seats with correct berth distribution")
    void createTrainWithCoachesAndSeats() {
        CreateTrainRequest request = new CreateTrainRequest(
            "RS-123",
            "Kolkata-Delhi Express",
            List.of(
                new CreateTrainRequest.CreateCoachRequest("B1", CoachType.AC_TIER_3, 8),
                new CreateTrainRequest.CreateCoachRequest("A1", CoachType.AC_TIER_2, 8)
            )
        );

        TrainResponse response = trainAdminService.createTrain(request);
        assertThat(response.id()).isNotNull();
        assertThat(response.trainNumber()).isEqualTo("RS-123");
        assertThat(response.totalCoaches()).isEqualTo(2);

        Train train = trainRepository.findWithCoachesAndSeatsById(response.id()).orElseThrow();
        assertThat(train.getCoaches()).hasSize(2);

        Coach b1 = train.getCoaches().stream()
            .filter(c -> c.getCoachNumber().equals("B1"))
            .findFirst()
            .orElseThrow();

        assertThat(b1.getTotalSeats()).isEqualTo(8);
        assertThat(b1.getSeats()).hasSize(8);

        // Check berth sequence according to mod 8 assignment:
        // Seat 1 -> Lower, Seat 2 -> Middle, Seat 3 -> Upper, Seat 4 -> Lower, Seat 7 -> Side Lower, Seat 8 -> Side Upper
        Seat seat1 = b1.getSeats().stream().filter(s -> s.getSeatNumber().equals("1")).findFirst().orElseThrow();
        Seat seat2 = b1.getSeats().stream().filter(s -> s.getSeatNumber().equals("2")).findFirst().orElseThrow();
        Seat seat3 = b1.getSeats().stream().filter(s -> s.getSeatNumber().equals("3")).findFirst().orElseThrow();
        Seat seat7 = b1.getSeats().stream().filter(s -> s.getSeatNumber().equals("7")).findFirst().orElseThrow();
        Seat seat8 = b1.getSeats().stream().filter(s -> s.getSeatNumber().equals("8")).findFirst().orElseThrow();

        assertThat(seat1.getSeatType()).isEqualTo(BerthType.LOWER);
        assertThat(seat2.getSeatType()).isEqualTo(BerthType.MIDDLE);
        assertThat(seat3.getSeatType()).isEqualTo(BerthType.UPPER);
        assertThat(seat7.getSeatType()).isEqualTo(BerthType.SIDE_LOWER);
        assertThat(seat8.getSeatType()).isEqualTo(BerthType.SIDE_UPPER);
    }
}

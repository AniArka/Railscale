package com.railscale.common.config;

import com.railscale.journey.service.JourneyService;
import com.railscale.journey.web.dto.CreateJourneyRequest;
import com.railscale.train.domain.CoachType;
import com.railscale.train.repository.StationRepository;
import com.railscale.train.repository.TrainRepository;
import com.railscale.train.service.TrainAdminService;
import com.railscale.train.web.dto.CreateStationRequest;
import com.railscale.train.web.dto.CreateTrainRequest;
import com.railscale.train.web.dto.StationResponse;
import com.railscale.train.web.dto.TrainResponse;
import com.railscale.user.domain.Role;
import com.railscale.user.repository.UserRepository;
import com.railscale.user.service.UserService;
import com.railscale.user.web.dto.RegisterUserRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@Profile("!test")
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final TrainAdminService trainAdminService;
    private final JourneyService journeyService;
    private final UserService userService;
    private final StationRepository stationRepository;
    private final TrainRepository trainRepository;
    private final UserRepository userRepository;

    public DataInitializer(
        TrainAdminService trainAdminService,
        JourneyService journeyService,
        UserService userService,
        StationRepository stationRepository,
        TrainRepository trainRepository,
        UserRepository userRepository
    ) {
        this.trainAdminService = trainAdminService;
        this.journeyService = journeyService;
        this.userService = userService;
        this.stationRepository = stationRepository;
        this.trainRepository = trainRepository;
        this.userRepository = userRepository;
    }

    @Override
    public void run(String... args) {
        if (stationRepository.count() > 0) {
            log.info("Database already seeded with core domain data. Skipping data initialization.");
            return;
        }

        log.info("Seeding initial core domain data for RailScale...");

        // 1. Seed Stations
        StationResponse howrah = trainAdminService.createStation(new CreateStationRequest("HWH", "Howrah Junction", "Kolkata"));
        StationResponse newDelhi = trainAdminService.createStation(new CreateStationRequest("NDLS", "New Delhi", "Delhi"));
        trainAdminService.createStation(new CreateStationRequest("GAYA", "Gaya Junction", "Gaya"));
        trainAdminService.createStation(new CreateStationRequest("DHN", "Dhanbad Junction", "Dhanbad"));

        // 2. Seed Train with Coaches and Seats
        CreateTrainRequest trainRequest = new CreateTrainRequest(
            "RS-123",
            "Kolkata-Delhi Superfast Express",
            List.of(
                new CreateTrainRequest.CreateCoachRequest("A1", CoachType.AC_TIER_2, 48),
                new CreateTrainRequest.CreateCoachRequest("B1", CoachType.AC_TIER_3, 64),
                new CreateTrainRequest.CreateCoachRequest("B2", CoachType.AC_TIER_3, 64),
                new CreateTrainRequest.CreateCoachRequest("S1", CoachType.SLEEPER, 72)
            )
        );
        TrainResponse train = trainAdminService.createTrain(trainRequest);

        // 3. Schedule Journey (automatically generates seat_inventory rows)
        Instant departureTime = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        Instant arrivalTime = departureTime.plus(17, ChronoUnit.HOURS);

        journeyService.scheduleJourney(new CreateJourneyRequest(
            train.id(),
            howrah.id(),
            newDelhi.id(),
            departureTime,
            arrivalTime
        ));

        // 4. Seed Demo Users
        userService.register(new RegisterUserRequest("admin@railscale.com", "Admin@12345", "System Admin", Role.ADMIN));
        userService.register(new RegisterUserRequest("passenger@railscale.com", "Pass@12345", "Alice Passenger", Role.USER));

        log.info("RailScale Phase 1 seed data initialization complete.");
    }
}

package com.railscale.journey.service;

import com.railscale.common.exception.BadRequestException;
import com.railscale.common.exception.ResourceNotFoundException;
import com.railscale.inventory.service.SeatInventoryService;
import com.railscale.inventory.web.dto.SeatInventoryResponse;
import com.railscale.journey.domain.Journey;
import com.railscale.journey.repository.JourneyRepository;
import com.railscale.journey.web.dto.CreateJourneyRequest;
import com.railscale.journey.web.dto.JourneyResponse;
import com.railscale.train.domain.Station;
import com.railscale.train.domain.Train;
import com.railscale.train.service.TrainAdminService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class JourneyService {

    private final JourneyRepository journeyRepository;
    private final TrainAdminService trainAdminService;
    private final SeatInventoryService seatInventoryService;

    public JourneyService(
        JourneyRepository journeyRepository,
        TrainAdminService trainAdminService,
        SeatInventoryService seatInventoryService
    ) {
        this.journeyRepository = journeyRepository;
        this.trainAdminService = trainAdminService;
        this.seatInventoryService = seatInventoryService;
    }

    @Transactional
    public JourneyResponse scheduleJourney(CreateJourneyRequest request) {
        if (!request.arrivalTime().isAfter(request.departureTime())) {
            throw new BadRequestException("Arrival time must be after departure time");
        }
        if (request.sourceStationId().equals(request.destinationStationId())) {
            throw new BadRequestException("Source and destination stations cannot be the same");
        }

        Train train = trainAdminService.getTrainEntity(request.trainId());
        Station sourceStation = trainAdminService.getStationEntity(request.sourceStationId());
        Station destinationStation = trainAdminService.getStationEntity(request.destinationStationId());

        Journey journey = new Journey(train, sourceStation, destinationStation, request.departureTime(), request.arrivalTime());
        Journey saved = journeyRepository.save(journey);

        // Pre-populate inventory for every physical seat on this train
        seatInventoryService.initializeInventoryForJourney(saved);
        long availableSeats = seatInventoryService.getAvailableSeatCount(saved.getId());

        return JourneyResponse.fromEntity(saved, availableSeats);
    }

    @Transactional(readOnly = true)
    public JourneyResponse getJourney(UUID id) {
        Journey journey = journeyRepository.findWithDetailsById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Journey not found with id: " + id));
        long availableSeats = seatInventoryService.getAvailableSeatCount(id);
        return JourneyResponse.fromEntity(journey, availableSeats);
    }

    @Transactional(readOnly = true)
    public List<JourneyResponse> searchJourneys(UUID sourceStationId, UUID destinationStationId, LocalDate date) {
        Instant startOfDay = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endOfDay = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusNanos(1);

        List<Journey> journeys = journeyRepository.findBySourceStationIdAndDestinationStationIdAndDepartureTimeBetween(
            sourceStationId,
            destinationStationId,
            startOfDay,
            endOfDay
        );

        return journeys.stream()
            .map(j -> {
                long availableSeats = seatInventoryService.getAvailableSeatCount(j.getId());
                return JourneyResponse.fromEntity(j, availableSeats);
            })
            .toList();
    }

    @Transactional(readOnly = true)
    public List<SeatInventoryResponse> getSeatsForJourney(UUID journeyId) {
        if (!journeyRepository.existsById(journeyId)) {
            throw new ResourceNotFoundException("Journey not found with id: " + journeyId);
        }
        return seatInventoryService.getSeatsForJourney(journeyId);
    }
}

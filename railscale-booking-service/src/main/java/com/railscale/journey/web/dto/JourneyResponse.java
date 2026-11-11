package com.railscale.journey.web.dto;

import com.railscale.journey.domain.Journey;
import com.railscale.journey.domain.JourneyStatus;

import java.time.Instant;
import java.util.UUID;

public record JourneyResponse(
    UUID id,
    UUID trainId,
    String trainNumber,
    String trainName,
    UUID sourceStationId,
    String sourceStationCode,
    String sourceStationName,
    UUID destinationStationId,
    String destinationStationCode,
    String destinationStationName,
    Instant departureTime,
    Instant arrivalTime,
    JourneyStatus status,
    long availableSeats
) {
    public static JourneyResponse fromEntity(Journey journey, long availableSeats) {
        return new JourneyResponse(
            journey.getId(),
            journey.getTrain().getId(),
            journey.getTrain().getTrainNumber(),
            journey.getTrain().getName(),
            journey.getSourceStation().getId(),
            journey.getSourceStation().getCode(),
            journey.getSourceStation().getName(),
            journey.getDestinationStation().getId(),
            journey.getDestinationStation().getCode(),
            journey.getDestinationStation().getName(),
            journey.getDepartureTime(),
            journey.getArrivalTime(),
            journey.getStatus(),
            availableSeats
        );
    }
}

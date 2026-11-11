package com.railscale.journey.web.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record CreateJourneyRequest(
    @NotNull(message = "Train ID is required")
    UUID trainId,

    @NotNull(message = "Source station ID is required")
    UUID sourceStationId,

    @NotNull(message = "Destination station ID is required")
    UUID destinationStationId,

    @NotNull(message = "Departure time is required")
    @Future(message = "Departure time must be in the future")
    Instant departureTime,

    @NotNull(message = "Arrival time is required")
    Instant arrivalTime
) {}

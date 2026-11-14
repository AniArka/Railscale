package com.railscale.booking.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record HoldBookingRequest(
    @NotNull(message = "journeyId is required")
    UUID journeyId,

    @NotEmpty(message = "seatIds cannot be empty")
    List<UUID> seatIds,

    UUID userId
) {
}

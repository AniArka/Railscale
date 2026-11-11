package com.railscale.train.web.dto;

import com.railscale.train.domain.CoachType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record CreateTrainRequest(
    @NotBlank(message = "Train number is required")
    String trainNumber,

    @NotBlank(message = "Train name is required")
    String name,

    @NotEmpty(message = "At least one coach is required")
    List<CreateCoachRequest> coaches
) {
    public record CreateCoachRequest(
        @NotBlank(message = "Coach number is required")
        String coachNumber,

        @NotNull(message = "Coach type is required")
        CoachType coachType,

        @Positive(message = "Total seats must be greater than zero")
        int totalSeats
    ) {}
}

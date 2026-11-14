package com.railscale.train.web.dto;

import com.railscale.train.domain.Coach;
import com.railscale.train.domain.CoachType;
import com.railscale.train.domain.Train;

import java.util.List;
import java.util.UUID;

public record TrainResponse(
    UUID id,
    String trainNumber,
    String name,
    int totalCoaches,
    List<CoachResponse> coaches
) {
    public record CoachResponse(
        UUID id,
        String coachNumber,
        CoachType coachType,
        int totalSeats
    ) {
        public static CoachResponse fromEntity(Coach coach) {
            return new CoachResponse(
                coach.getId(),
                coach.getCoachNumber(),
                coach.getCoachType(),
                coach.getTotalSeats()
            );
        }
    }

    public static TrainResponse fromEntity(Train train) {
        List<CoachResponse> coachResponses = train.getCoaches() != null
            ? train.getCoaches().stream().map(CoachResponse::fromEntity).toList()
            : List.of();

        return new TrainResponse(
            train.getId(),
            train.getTrainNumber(),
            train.getName(),
            train.getTotalCoaches(),
            coachResponses
        );
    }
}

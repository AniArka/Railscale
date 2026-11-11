package com.railscale.inventory.web.dto;

import com.railscale.inventory.domain.SeatInventory;
import com.railscale.inventory.domain.SeatStatus;
import com.railscale.train.domain.BerthType;
import com.railscale.train.domain.CoachType;

import java.util.UUID;

public record SeatInventoryResponse(
    UUID inventoryId,
    UUID journeyId,
    UUID seatId,
    String coachNumber,
    CoachType coachType,
    String seatNumber,
    BerthType berthType,
    SeatStatus status
) {
    public static SeatInventoryResponse fromEntity(SeatInventory inv) {
        return new SeatInventoryResponse(
            inv.getId(),
            inv.getJourneyId(),
            inv.getSeat().getId(),
            inv.getSeat().getCoach().getCoachNumber(),
            inv.getSeat().getCoach().getCoachType(),
            inv.getSeat().getSeatNumber(),
            inv.getSeat().getSeatType(),
            inv.getStatus()
        );
    }
}

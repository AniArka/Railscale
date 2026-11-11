package com.railscale.inventory.service;

import com.railscale.inventory.domain.SeatInventory;
import com.railscale.inventory.domain.SeatStatus;
import com.railscale.inventory.repository.SeatInventoryRepository;
import com.railscale.inventory.web.dto.SeatInventoryResponse;
import com.railscale.journey.domain.Journey;
import com.railscale.train.domain.Seat;
import com.railscale.train.repository.SeatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class SeatInventoryService {

    private final SeatInventoryRepository seatInventoryRepository;
    private final SeatRepository seatRepository;

    public SeatInventoryService(SeatInventoryRepository seatInventoryRepository, SeatRepository seatRepository) {
        this.seatInventoryRepository = seatInventoryRepository;
        this.seatRepository = seatRepository;
    }

    @Transactional
    public List<SeatInventory> initializeInventoryForJourney(Journey journey) {
        List<Seat> seats = seatRepository.findByCoachTrainId(journey.getTrain().getId());
        List<SeatInventory> inventories = new ArrayList<>(seats.size());

        for (Seat seat : seats) {
            inventories.add(new SeatInventory(journey.getId(), seat));
        }

        return seatInventoryRepository.saveAll(inventories);
    }

    @Transactional(readOnly = true)
    public List<SeatInventoryResponse> getSeatsForJourney(UUID journeyId) {
        return seatInventoryRepository.findWithSeatDetailsByJourneyId(journeyId).stream()
            .map(SeatInventoryResponse::fromEntity)
            .toList();
    }

    @Transactional(readOnly = true)
    public long getAvailableSeatCount(UUID journeyId) {
        return seatInventoryRepository.countByJourneyIdAndStatus(journeyId, SeatStatus.AVAILABLE);
    }
}

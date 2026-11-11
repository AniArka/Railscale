package com.railscale.inventory.repository;

import com.railscale.inventory.domain.SeatInventory;
import com.railscale.inventory.domain.SeatStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SeatInventoryRepository extends JpaRepository<SeatInventory, UUID> {

    Optional<SeatInventory> findByJourneyIdAndSeatId(UUID journeyId, UUID seatId);

    @EntityGraph(attributePaths = {"seat", "seat.coach"})
    List<SeatInventory> findWithSeatDetailsByJourneyId(UUID journeyId);

    List<SeatInventory> findByJourneyIdAndIdInOrderById(UUID journeyId, Collection<UUID> ids);

    long countByJourneyIdAndStatus(UUID journeyId, SeatStatus status);

    boolean existsByJourneyIdAndSeatId(UUID journeyId, UUID seatId);
}

package com.railscale.journey.repository;

import com.railscale.journey.domain.Journey;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JourneyRepository extends JpaRepository<Journey, UUID> {

    @EntityGraph(attributePaths = {"train", "sourceStation", "destinationStation"})
    List<Journey> findBySourceStationIdAndDestinationStationIdAndDepartureTimeBetween(
        UUID sourceStationId,
        UUID destinationStationId,
        Instant start,
        Instant end
    );

    @EntityGraph(attributePaths = {"train", "sourceStation", "destinationStation"})
    Optional<Journey> findWithDetailsById(UUID id);
}

package com.railscale.search.repository;

import com.railscale.search.domain.JourneySearchProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface JourneySearchProjectionRepository extends JpaRepository<JourneySearchProjection, UUID> {

    List<JourneySearchProjection> findBySourceIgnoreCaseAndDestinationIgnoreCase(
        String source, String destination
    );

    List<JourneySearchProjection> findBySourceIgnoreCaseAndDestinationIgnoreCaseAndDepartureTimeBetween(
        String source, String destination, Instant start, Instant end
    );
}

package com.railscale.search.service;

import com.railscale.search.domain.JourneySearchProjection;
import com.railscale.search.repository.JourneySearchProjectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class SearchService {

    private final JourneySearchProjectionRepository projectionRepository;

    public SearchService(JourneySearchProjectionRepository projectionRepository) {
        this.projectionRepository = projectionRepository;
    }

    public List<JourneySearchProjection> searchJourneys(String source, String destination) {
        return projectionRepository.findBySourceIgnoreCaseAndDestinationIgnoreCase(source, destination);
    }

    public List<JourneySearchProjection> searchJourneysByDate(
        String source, String destination, Instant start, Instant end
    ) {
        return projectionRepository.findBySourceIgnoreCaseAndDestinationIgnoreCaseAndDepartureTimeBetween(
            source, destination, start, end
        );
    }

    @Transactional
    public JourneySearchProjection saveProjection(JourneySearchProjection projection) {
        return projectionRepository.save(projection);
    }

    public JourneySearchProjection getById(UUID journeyId) {
        return projectionRepository.findById(journeyId).orElse(null);
    }
}

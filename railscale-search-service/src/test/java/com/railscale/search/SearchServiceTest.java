package com.railscale.search;

import com.railscale.search.domain.JourneySearchProjection;
import com.railscale.search.service.SearchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = SearchServiceApplication.class)
@ActiveProfiles("test")
@Transactional
class SearchServiceTest {

    @Autowired
    private SearchService searchService;

    @Test
    @DisplayName("Should populate and query journey search projection")
    void testSearchJourneys() {
        UUID journeyId = UUID.randomUUID();
        Instant now = Instant.now();
        Instant departure = now.plus(1, ChronoUnit.DAYS);
        Instant arrival = departure.plus(4, ChronoUnit.HOURS);

        JourneySearchProjection projection = new JourneySearchProjection(
            journeyId, "DELHI", "MUMBAI", departure, arrival, 120, now
        );
        searchService.saveProjection(projection);

        List<JourneySearchProjection> results = searchService.searchJourneys("DELHI", "MUMBAI");

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).getJourneyId()).isEqualTo(journeyId);
        assertThat(results.get(0).getAvailableSeatCount()).isEqualTo(120);
    }
}

package com.railscale.search.web;

import com.railscale.search.domain.JourneySearchProjection;
import com.railscale.search.service.SearchService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/journeys")
    public ResponseEntity<List<JourneySearchProjection>> searchJourneys(
        @RequestParam String source,
        @RequestParam String destination,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        if (date != null) {
            var start = date.atStartOfDay().toInstant(ZoneOffset.UTC);
            var end = date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
            return ResponseEntity.ok(searchService.searchJourneysByDate(source, destination, start, end));
        }
        return ResponseEntity.ok(searchService.searchJourneys(source, destination));
    }
}

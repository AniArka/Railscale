package com.railscale.journey.web;

import com.railscale.inventory.web.dto.SeatInventoryResponse;
import com.railscale.journey.service.JourneyService;
import com.railscale.journey.web.dto.CreateJourneyRequest;
import com.railscale.journey.web.dto.JourneyResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/journeys")
public class JourneyController {

    private final JourneyService journeyService;

    public JourneyController(JourneyService journeyService) {
        this.journeyService = journeyService;
    }

    @PostMapping
    public ResponseEntity<JourneyResponse> scheduleJourney(@Valid @RequestBody CreateJourneyRequest request) {
        JourneyResponse response = journeyService.scheduleJourney(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<JourneyResponse> getJourney(@PathVariable UUID id) {
        return ResponseEntity.ok(journeyService.getJourney(id));
    }

    @GetMapping("/search")
    public ResponseEntity<List<JourneyResponse>> searchJourneys(
        @RequestParam UUID sourceStationId,
        @RequestParam UUID destinationStationId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ResponseEntity.ok(journeyService.searchJourneys(sourceStationId, destinationStationId, date));
    }

    @GetMapping("/{journeyId}/seats")
    public ResponseEntity<List<SeatInventoryResponse>> getSeats(@PathVariable UUID journeyId) {
        return ResponseEntity.ok(journeyService.getSeatsForJourney(journeyId));
    }
}

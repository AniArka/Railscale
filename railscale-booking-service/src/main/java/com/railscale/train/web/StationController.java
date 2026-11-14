package com.railscale.train.web;

import com.railscale.train.service.TrainAdminService;
import com.railscale.train.web.dto.CreateStationRequest;
import com.railscale.train.web.dto.StationResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/stations")
public class StationController {

    private final TrainAdminService trainAdminService;

    public StationController(TrainAdminService trainAdminService) {
        this.trainAdminService = trainAdminService;
    }

    @PostMapping
    public ResponseEntity<StationResponse> createStation(@Valid @RequestBody CreateStationRequest request) {
        StationResponse response = trainAdminService.createStation(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<StationResponse>> getAllStations() {
        return ResponseEntity.ok(trainAdminService.getAllStations());
    }
}

package com.railscale.train.web.dto;

import com.railscale.train.domain.Station;

import java.util.UUID;

public record StationResponse(
    UUID id,
    String code,
    String name,
    String city
) {
    public static StationResponse fromEntity(Station station) {
        return new StationResponse(
            station.getId(),
            station.getCode(),
            station.getName(),
            station.getCity()
        );
    }
}

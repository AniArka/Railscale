package com.railscale.train.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateStationRequest(
    @NotBlank(message = "Station code is required")
    @Size(min = 2, max = 10, message = "Station code must be between 2 and 10 characters")
    String code,

    @NotBlank(message = "Station name is required")
    String name,

    @NotBlank(message = "City is required")
    String city
) {}

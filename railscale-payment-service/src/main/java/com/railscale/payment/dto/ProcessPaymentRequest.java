package com.railscale.payment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record ProcessPaymentRequest(
    @NotNull UUID bookingId,
    @NotNull @Positive BigDecimal amount,
    boolean simulateFailure
) {}

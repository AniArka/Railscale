package com.railscale.booking.dto;

import com.railscale.payment.domain.PaymentStatus;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SimulatePaymentRequest(
    @NotNull(message = "Payment status is required")
    PaymentStatus status,

    UUID eventId
) {
}

package com.railscale.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResultResponse(
    UUID paymentId,
    UUID bookingId,
    BigDecimal amount,
    String status,
    String message,
    Instant timestamp
) {}

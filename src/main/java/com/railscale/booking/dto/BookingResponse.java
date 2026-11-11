package com.railscale.booking.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.railscale.booking.domain.Booking;
import com.railscale.booking.domain.BookingItem;
import com.railscale.booking.domain.BookingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BookingResponse(
    UUID id,
    UUID userId,
    UUID journeyId,
    BookingStatus status,
    BigDecimal totalAmount,
    Instant paymentInitiatedAt,
    Instant expiresAt,
    List<BookingItemResponse> items,
    Instant createdAt
) {
    @JsonProperty("bookingId")
    public UUID bookingId() {
        return id;
    }

    public record BookingItemResponse(
        UUID id,
        UUID inventoryId,
        BigDecimal price
    ) {
        public static BookingItemResponse fromEntity(BookingItem item) {
            return new BookingItemResponse(
                item.getId(),
                item.getInventory() != null ? item.getInventory().getId() : null,
                item.getPrice()
            );
        }
    }

    public static BookingResponse fromEntity(Booking booking) {
        return fromEntity(booking, null);
    }

    public static BookingResponse fromEntity(Booking booking, Instant expiresAt) {
        List<BookingItemResponse> itemResponses = booking.getItems() != null
            ? booking.getItems().stream().map(BookingItemResponse::fromEntity).toList()
            : List.of();

        if (expiresAt == null && booking.getItems() != null && !booking.getItems().isEmpty()) {
            BookingItem first = booking.getItems().get(0);
            if (first.getInventory() != null) {
                expiresAt = first.getInventory().getHeldUntil();
            }
        }

        return new BookingResponse(
            booking.getId(),
            booking.getUserId(),
            booking.getJourneyId(),
            booking.getStatus(),
            booking.getTotalAmount(),
            booking.getPaymentInitiatedAt(),
            expiresAt,
            itemResponses,
            booking.getCreatedAt()
        );
    }
}

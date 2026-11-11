package com.railscale.booking.dto;

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
    List<BookingItemResponse> items,
    Instant createdAt
) {
    public record BookingItemResponse(
        UUID id,
        UUID inventoryId,
        BigDecimal price
    ) {
        public static BookingItemResponse fromEntity(BookingItem item) {
            return new BookingItemResponse(
                item.getId(),
                item.getInventory().getId(),
                item.getPrice()
            );
        }
    }

    public static BookingResponse fromEntity(Booking booking) {
        List<BookingItemResponse> itemResponses = booking.getItems() != null
            ? booking.getItems().stream().map(BookingItemResponse::fromEntity).toList()
            : List.of();

        return new BookingResponse(
            booking.getId(),
            booking.getUserId(),
            booking.getJourneyId(),
            booking.getStatus(),
            booking.getTotalAmount(),
            booking.getPaymentInitiatedAt(),
            itemResponses,
            booking.getCreatedAt()
        );
    }
}

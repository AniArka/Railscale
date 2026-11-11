package com.railscale.booking.repository;

import com.railscale.booking.domain.Booking;
import com.railscale.booking.domain.BookingStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {

    @EntityGraph(attributePaths = {"items", "items.inventory", "items.inventory.seat"})
    Optional<Booking> findWithItemsById(UUID id);

    List<Booking> findByUserId(UUID userId);

    List<Booking> findByStatusAndPaymentInitiatedAtBefore(BookingStatus status, Instant cutoff);
}

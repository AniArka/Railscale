package com.railscale.booking.repository;

import com.railscale.booking.domain.Booking;
import com.railscale.booking.domain.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * Section 27: Pessimistic write lock on booking.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Section 23.2 & Section 27: Find expired payment-pending bookings with lock for reconciliation.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.status = :status AND b.paymentInitiatedAt < :cutoff ORDER BY b.id ASC")
    List<Booking> findExpiredPaymentPendingForUpdate(@Param("status") BookingStatus status, @Param("cutoff") Instant cutoff);
}

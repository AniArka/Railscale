package com.railscale.inventory.repository;

import com.railscale.inventory.domain.SeatInventory;
import com.railscale.inventory.domain.SeatStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SeatInventoryRepository extends JpaRepository<SeatInventory, UUID> {

    Optional<SeatInventory> findByJourneyIdAndSeatId(UUID journeyId, UUID seatId);

    @Query("SELECT s FROM SeatInventory s LEFT JOIN FETCH s.seat st LEFT JOIN FETCH st.coach c WHERE s.journeyId = :journeyId ORDER BY s.id ASC")
    List<SeatInventory> findWithSeatDetailsByJourneyId(@Param("journeyId") UUID journeyId);

    List<SeatInventory> findByJourneyIdAndIdInOrderById(UUID journeyId, Collection<UUID> ids);

    long countByJourneyIdAndStatus(UUID journeyId, SeatStatus status);

    boolean existsByJourneyIdAndSeatId(UUID journeyId, UUID seatId);

    /**
     * Invariant 3 & Section 27: Pessimistic write lock on inventory rows in canonical ID order.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SeatInventory s WHERE s.id IN :ids ORDER BY s.id ASC")
    List<SeatInventory> findAllForUpdate(@Param("ids") Collection<UUID> ids);

    /**
     * Invariant 3: Lock inventory rows for a journey by seat IDs in canonical inventory ID order.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SeatInventory s WHERE s.journeyId = :journeyId AND s.seat.id IN :seatIds ORDER BY s.id ASC")
    List<SeatInventory> findByJourneyIdAndSeatIdInForUpdate(@Param("journeyId") UUID journeyId, @Param("seatIds") Collection<UUID> seatIds);

    /**
     * Section 21 & Section 42: Deterministically lock all seat inventory items associated with a booking.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT si FROM SeatInventory si, BookingItem bi WHERE bi.booking.id = :bookingId AND bi.inventory.id = si.id ORDER BY si.id ASC")
    List<SeatInventory> findByBookingIdForUpdate(@Param("bookingId") UUID bookingId);

    /**
     * Section 23.1 & 26: Lock expired holds deterministically for reconciliation.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SeatInventory s WHERE s.status = :status AND s.heldUntil < :now ORDER BY s.id ASC")
    List<SeatInventory> findExpiredHoldsForUpdate(@Param("status") SeatStatus status, @Param("now") Instant now);
}

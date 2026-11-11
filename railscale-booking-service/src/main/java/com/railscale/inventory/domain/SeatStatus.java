package com.railscale.inventory.domain;

/**
 * Seat status state machine according to RailScale technical specification v2.0.
 * Invariant: PAYMENT_PENDING is a booking-level state; seats remain HELD during payment pending.
 */
public enum SeatStatus {
    AVAILABLE,
    HELD,
    BOOKED
}

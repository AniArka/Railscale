package com.railscale.common.event;

/**
 * Kafka topic name constants (Architecture Section 12).
 * Topics are organized by domain aggregate.
 */
public final class KafkaTopics {

    private KafkaTopics() {
        // Utility class
    }

    /** Booking lifecycle events: BookingHeld, BookingConfirmed, BookingCancelled */
    public static final String BOOKING_EVENTS = "booking.events";

    /** Payment lifecycle events: PaymentInitiated, PaymentCompleted, PaymentFailed */
    public static final String PAYMENT_EVENTS = "payment.events";

    /** Inventory events: SeatReleased */
    public static final String INVENTORY_EVENTS = "inventory.events";

    /** Notification events (consumed by notification service) */
    public static final String NOTIFICATION_EVENTS = "notification.events";

    /** Refund events: RefundRequired */
    public static final String REFUND_EVENTS = "refund.events";
}

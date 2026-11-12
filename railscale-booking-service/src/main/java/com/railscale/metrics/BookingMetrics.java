package com.railscale.metrics;

import com.railscale.booking.domain.BookingStatus;
import com.railscale.booking.repository.BookingRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Custom Prometheus domain metrics for RailScale Booking Service.
 * Implements Section 41 (Observability) specifications:
 * - booking_requests_total
 * - booking_success_total
 * - booking_failure_total (tagged by reason)
 * - seat_hold_total
 * - seat_hold_expired_total
 * - seat_contention_total
 * - booking_latency_seconds
 * - redis_operation_latency
 * - postgres_operation_latency
 * - kafka_publish_failures
 * - active_holds
 * - confirmed_bookings
 * - refund_required_total
 */
@Component
public class BookingMetrics {

    private final MeterRegistry registry;
    private final Counter bookingRequestsTotal;
    private final Counter bookingSuccessTotal;
    private final Counter seatHoldTotal;
    private final Counter seatHoldExpiredTotal;
    private final Counter seatContentionTotal;
    private final Counter refundRequiredTotal;
    private final Counter kafkaPublishFailures;
    private final Timer bookingLatencyTimer;
    private final Timer redisOperationTimer;
    private final Timer postgresOperationTimer;

    public BookingMetrics(
        @org.springframework.beans.factory.annotation.Autowired(required = false) MeterRegistry registry,
        BookingRepository bookingRepository
    ) {
        this.registry = registry != null ? registry : new io.micrometer.core.instrument.simple.SimpleMeterRegistry();

        this.bookingRequestsTotal = Counter.builder("booking_requests_total")
                .description("Total number of booking hold/create requests initiated")
                .register(registry);

        this.bookingSuccessTotal = Counter.builder("booking_success_total")
                .description("Total number of bookings successfully confirmed")
                .register(registry);

        this.seatHoldTotal = Counter.builder("seat_hold_total")
                .description("Total number of individual seats placed on hold")
                .register(registry);

        this.seatHoldExpiredTotal = Counter.builder("seat_hold_expired_total")
                .description("Total number of seat holds expired by reconciliation")
                .register(registry);

        this.seatContentionTotal = Counter.builder("seat_contention_total")
                .description("Total number of seat conflicts or lock contention occurrences")
                .register(registry);

        this.refundRequiredTotal = Counter.builder("refund_required_total")
                .description("Total number of refund events emitted due to late payments or cancellations")
                .register(registry);

        this.kafkaPublishFailures = Counter.builder("kafka_publish_failures")
                .description("Total number of Kafka publication failures in outbox processing")
                .register(registry);

        this.bookingLatencyTimer = Timer.builder("booking_latency_seconds")
                .description("Latency distribution of booking transactions")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        this.redisOperationTimer = Timer.builder("redis_operation_latency")
                .description("Latency of Redis lock and cache operations")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        this.postgresOperationTimer = Timer.builder("postgres_operation_latency")
                .description("Latency of PostgreSQL transactional database operations")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        // Dynamic status gauges
        registry.gauge("active_holds", bookingRepository,
                repo -> repo.countByStatus(BookingStatus.HELD));
        registry.gauge("confirmed_bookings", bookingRepository,
                repo -> repo.countByStatus(BookingStatus.CONFIRMED));
    }

    public void incrementBookingRequests() {
        bookingRequestsTotal.increment();
    }

    public void incrementBookingSuccess() {
        bookingSuccessTotal.increment();
    }

    public void incrementBookingFailure(String reason) {
        Counter.builder("booking_failure_total")
                .tag("reason", reason != null ? reason : "unknown")
                .description("Total number of failed booking attempts partitioned by reason")
                .register(registry)
                .increment();
    }

    public void incrementSeatHold(int count) {
        seatHoldTotal.increment(count);
    }

    public void incrementSeatHoldExpired(int count) {
        seatHoldExpiredTotal.increment(count);
    }

    public void incrementSeatContention() {
        seatContentionTotal.increment();
    }

    public void incrementRefundRequired() {
        refundRequiredTotal.increment();
    }

    public void incrementKafkaPublishFailures() {
        kafkaPublishFailures.increment();
    }

    public void recordBookingLatency(Duration duration) {
        bookingLatencyTimer.record(duration);
    }

    public void recordRedisLatency(Duration duration) {
        redisOperationTimer.record(duration);
    }

    public void recordPostgresLatency(Duration duration) {
        postgresOperationTimer.record(duration);
    }

    public MeterRegistry getRegistry() {
        return registry;
    }
}

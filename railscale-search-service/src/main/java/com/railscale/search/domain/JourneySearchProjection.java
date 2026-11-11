package com.railscale.search.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Journey search projection (Architecture Section 28).
 * Read-heavy projection populated asynchronously via Kafka inventory events.
 * Decouples search queries from the hot seat_inventory table.
 */
@Entity
@Table(name = "journey_search_projection")
public class JourneySearchProjection {

    @Id
    @Column(name = "journey_id", nullable = false)
    private UUID journeyId;

    @Column(name = "source", nullable = false)
    private String source;

    @Column(name = "destination", nullable = false)
    private String destination;

    @Column(name = "departure_time", nullable = false)
    private Instant departureTime;

    @Column(name = "arrival_time", nullable = false)
    private Instant arrivalTime;

    @Column(name = "available_seat_count", nullable = false)
    private int availableSeatCount;

    @Column(name = "last_updated", nullable = false)
    private Instant lastUpdated;

    public JourneySearchProjection() {
    }

    public JourneySearchProjection(UUID journeyId, String source, String destination,
                                   Instant departureTime, Instant arrivalTime,
                                   int availableSeatCount, Instant lastUpdated) {
        this.journeyId = journeyId;
        this.source = source;
        this.destination = destination;
        this.departureTime = departureTime;
        this.arrivalTime = arrivalTime;
        this.availableSeatCount = availableSeatCount;
        this.lastUpdated = lastUpdated;
    }

    public UUID getJourneyId() {
        return journeyId;
    }

    public void setJourneyId(UUID journeyId) {
        this.journeyId = journeyId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public Instant getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(Instant departureTime) {
        this.departureTime = departureTime;
    }

    public Instant getArrivalTime() {
        return arrivalTime;
    }

    public void setArrivalTime(Instant arrivalTime) {
        this.arrivalTime = arrivalTime;
    }

    public int getAvailableSeatCount() {
        return availableSeatCount;
    }

    public void setAvailableSeatCount(int availableSeatCount) {
        this.availableSeatCount = availableSeatCount;
    }

    public Instant getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Instant lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}

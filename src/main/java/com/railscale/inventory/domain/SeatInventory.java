package com.railscale.inventory.domain;

import com.railscale.common.model.BaseEntity;
import com.railscale.train.domain.Seat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "seat_inventory",
    uniqueConstraints = @UniqueConstraint(name = "uq_journey_seat", columnNames = {"journey_id", "seat_id"})
)
public class SeatInventory extends BaseEntity {

    @Column(name = "journey_id", nullable = false)
    private UUID journeyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SeatStatus status = SeatStatus.AVAILABLE;

    @Column(name = "held_by")
    private UUID heldBy;

    @Column(name = "held_until")
    private Instant heldUntil;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    public SeatInventory() {
    }

    public SeatInventory(UUID journeyId, Seat seat) {
        this.journeyId = journeyId;
        this.seat = seat;
        this.status = SeatStatus.AVAILABLE;
        this.version = 0L;
    }

    public UUID getJourneyId() {
        return journeyId;
    }

    public void setJourneyId(UUID journeyId) {
        this.journeyId = journeyId;
    }

    public Seat getSeat() {
        return seat;
    }

    public void setSeat(Seat seat) {
        this.seat = seat;
    }

    public SeatStatus getStatus() {
        return status;
    }

    public void setStatus(SeatStatus status) {
        this.status = status;
    }

    public UUID getHeldBy() {
        return heldBy;
    }

    public void setHeldBy(UUID heldBy) {
        this.heldBy = heldBy;
    }

    public Instant getHeldUntil() {
        return heldUntil;
    }

    public void setHeldUntil(Instant heldUntil) {
        this.heldUntil = heldUntil;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}

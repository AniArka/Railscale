package com.railscale.train.domain;

import com.railscale.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "seats",
    uniqueConstraints = @UniqueConstraint(name = "uq_coach_seat", columnNames = {"coach_id", "seat_number"})
)
public class Seat extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coach_id", nullable = false)
    private Coach coach;

    @Column(name = "seat_number", nullable = false, length = 10)
    private String seatNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "seat_type", nullable = false, length = 30)
    private BerthType seatType;

    public Seat() {
    }

    public Seat(Coach coach, String seatNumber, BerthType seatType) {
        this.coach = coach;
        this.seatNumber = seatNumber;
        this.seatType = seatType;
    }

    public Coach getCoach() {
        return coach;
    }

    public void setCoach(Coach coach) {
        this.coach = coach;
    }

    public String getSeatNumber() {
        return seatNumber;
    }

    public void setSeatNumber(String seatNumber) {
        this.seatNumber = seatNumber;
    }

    public BerthType getSeatType() {
        return seatType;
    }

    public void setSeatType(BerthType seatType) {
        this.seatType = seatType;
    }
}

package com.railscale.train.domain;

import com.railscale.common.model.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(
    name = "coaches",
    uniqueConstraints = @UniqueConstraint(name = "uq_train_coach", columnNames = {"train_id", "coach_number"})
)
public class Coach extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "train_id", nullable = false)
    private Train train;

    @Column(name = "coach_number", nullable = false, length = 10)
    private String coachNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "coach_type", nullable = false, length = 30)
    private CoachType coachType;

    @Column(name = "total_seats", nullable = false)
    private int totalSeats = 0;

    @OneToMany(mappedBy = "coach", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Seat> seats = new LinkedHashSet<>();

    public Coach() {
    }

    public Coach(Train train, String coachNumber, CoachType coachType) {
        this.train = train;
        this.coachNumber = coachNumber;
        this.coachType = coachType;
    }

    public void addSeat(Seat seat) {
        seats.add(seat);
        seat.setCoach(this);
        this.totalSeats = seats.size();
    }

    public Train getTrain() {
        return train;
    }

    public void setTrain(Train train) {
        this.train = train;
    }

    public String getCoachNumber() {
        return coachNumber;
    }

    public void setCoachNumber(String coachNumber) {
        this.coachNumber = coachNumber;
    }

    public CoachType getCoachType() {
        return coachType;
    }

    public void setCoachType(CoachType coachType) {
        this.coachType = coachType;
    }

    public int getTotalSeats() {
        return totalSeats;
    }

    public void setTotalSeats(int totalSeats) {
        this.totalSeats = totalSeats;
    }

    public Set<Seat> getSeats() {
        return seats;
    }

    public void setSeats(Set<Seat> seats) {
        this.seats = seats;
        this.totalSeats = seats != null ? seats.size() : 0;
    }
}

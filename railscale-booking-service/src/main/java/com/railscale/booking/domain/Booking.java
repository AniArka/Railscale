package com.railscale.booking.domain;

import com.railscale.common.model.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "bookings")
public class Booking extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "journey_id", nullable = false)
    private UUID journeyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private BookingStatus status = BookingStatus.PENDING;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "payment_initiated_at")
    private Instant paymentInitiatedAt;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BookingItem> items = new ArrayList<>();

    public Booking() {
    }

    public Booking(UUID userId, UUID journeyId, BigDecimal totalAmount) {
        this.userId = userId;
        this.journeyId = journeyId;
        this.totalAmount = totalAmount != null ? totalAmount : BigDecimal.ZERO;
        this.status = BookingStatus.PENDING;
    }

    public void addItem(BookingItem item) {
        items.add(item);
        item.setBooking(this);
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getJourneyId() {
        return journeyId;
    }

    public void setJourneyId(UUID journeyId) {
        this.journeyId = journeyId;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public Instant getPaymentInitiatedAt() {
        return paymentInitiatedAt;
    }

    public void setPaymentInitiatedAt(Instant paymentInitiatedAt) {
        this.paymentInitiatedAt = paymentInitiatedAt;
    }

    public List<BookingItem> getItems() {
        return items;
    }

    public void setItems(List<BookingItem> items) {
        this.items = items;
    }
}

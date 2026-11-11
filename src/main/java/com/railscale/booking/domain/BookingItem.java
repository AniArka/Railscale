package com.railscale.booking.domain;

import com.railscale.common.model.BaseEntity;
import com.railscale.inventory.domain.SeatInventory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "booking_items")
public class BookingItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_id", nullable = false)
    private SeatInventory inventory;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    public BookingItem() {
    }

    public BookingItem(Booking booking, SeatInventory inventory, BigDecimal price) {
        this.booking = booking;
        this.inventory = inventory;
        this.price = price;
    }

    public Booking getBooking() {
        return booking;
    }

    public void setBooking(Booking booking) {
        this.booking = booking;
    }

    public SeatInventory getInventory() {
        return inventory;
    }

    public void setInventory(SeatInventory inventory) {
        this.inventory = inventory;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }
}

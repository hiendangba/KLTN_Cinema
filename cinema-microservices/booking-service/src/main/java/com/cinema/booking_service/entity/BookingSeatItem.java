package com.cinema.booking_service.entity;

import com.cinema.Enum.HallEnum;
import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "booking_seat_item",
        uniqueConstraints = @UniqueConstraint(name = "uk_booking_seat_code", columnNames = {"booking_id", "seat_code"}))
@Getter
@Setter
public class BookingSeatItem {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "seat_code", nullable = false, length = 20)
    private String seatCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "seat_type", nullable = false, length = 20)
    private HallEnum.SeatType seatType;

    @Column(name = "seat_price_snapshot", nullable = false, precision = 12, scale = 2)
    private BigDecimal seatPriceSnapshot;

    @Column(name = "time_created", nullable = false, updatable = false)
    private LocalDateTime timeCreated;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UuidCreator.getTimeOrderedEpoch();
        }
        timeCreated = LocalDateTime.now();
    }
}


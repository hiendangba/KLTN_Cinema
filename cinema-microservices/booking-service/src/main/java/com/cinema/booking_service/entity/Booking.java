package com.cinema.booking_service.entity;

import com.cinema.booking_service.enums.BookingStatus;
import com.cinema.booking_service.enums.PaymentStatus;
import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "booking", indexes = {
        @Index(name = "idx_booking_report_showtime", columnList = "is_deleted, booking_status, showtime_start_date_time"),
        @Index(name = "idx_booking_report_cinema_film", columnList = "is_deleted, cinema_id, film_id"),
        @Index(name = "idx_booking_user_active", columnList = "user_id, is_deleted, booking_status, reserved_until"),
        @Index(name = "idx_booking_showtime_status", columnList = "showtime_id, is_deleted, booking_status")
})
@Getter
@Setter
public class Booking {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "showtime_id", columnDefinition = "uuid", nullable = false)
    private UUID showtimeId;

    @Column(name = "cinema_id", columnDefinition = "uuid", nullable = false)
    private UUID cinemaId;

    @Column(name = "film_id", columnDefinition = "uuid")
    private UUID filmId;

    @Column(name = "film_title", length = 255)
    private String filmTitle;

    @Column(name = "showtime_start_date_time")
    private LocalDateTime showtimeStartDateTime;

    @Column(name = "showtime_end_date_time")
    private LocalDateTime showtimeEndDateTime;

    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID userId;

    @Embedded
    private CustomerInfo customerInfo;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private PaymentStatus paymentStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "booking_status", nullable = false, length = 20)
    private BookingStatus bookingStatus;

    @Column(name = "ticket_subtotal", nullable = false, precision = 12, scale = 2)
    private BigDecimal ticketSubtotal;

    @Column(name = "product_subtotal", nullable = false, precision = 12, scale = 2)
    private BigDecimal productSubtotal;

    @Column(name = "final_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal finalAmount;

    @Column(name = "promotion_id", columnDefinition = "uuid")
    private UUID promotionId;

    @Column(name = "promotion_code", length = 80)
    private String promotionCode;

    @Column(name = "promotion_name", length = 120)
    private String promotionName;

    @Column(name = "promotion_discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal promotionDiscountAmount;

    @Column(name = "loyalty_points_used", nullable = false)
    private Long loyaltyPointsUsed;

    @Column(name = "loyalty_points_earned", nullable = false)
    private Long loyaltyPointsEarned;

    @Column(name = "payable_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal payableAmount;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted;

    @Column(name = "reserved_until", nullable = false)
    private LocalDateTime reservedUntil;

    @Column(name = "time_created", nullable = false, updatable = false)
    private LocalDateTime timeCreated;

    @Column(name = "time_updated", nullable = false)
    private LocalDateTime timeUpdated;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BookingSeatItem> seatItems = new ArrayList<>();

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BookingProductItem> productItems = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UuidCreator.getTimeOrderedEpoch();
        }
        if (paymentStatus == null) {
            paymentStatus = PaymentStatus.UNPAID;
        }
        if (bookingStatus == null) {
            bookingStatus = BookingStatus.PENDING;
        }
        if (ticketSubtotal == null) {
            ticketSubtotal = BigDecimal.ZERO;
        }
        if (productSubtotal == null) {
            productSubtotal = BigDecimal.ZERO;
        }
        if (finalAmount == null) {
            finalAmount = BigDecimal.ZERO;
        }
        if (promotionDiscountAmount == null) {
            promotionDiscountAmount = BigDecimal.ZERO;
        }
        if (loyaltyPointsUsed == null) {
            loyaltyPointsUsed = 0L;
        }
        if (loyaltyPointsEarned == null) {
            loyaltyPointsEarned = 0L;
        }
        if (payableAmount == null) {
            payableAmount = finalAmount.subtract(promotionDiscountAmount);
        }
        if (isDeleted == null) {
            isDeleted = false;
        }
        LocalDateTime now = LocalDateTime.now();
        timeCreated = now;
        timeUpdated = now;
    }

    @PreUpdate
    public void preUpdate() {
        timeUpdated = LocalDateTime.now();
    }
}

package com.cinema.user_service.entity;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_loyalty_ledger")
@Getter
@Setter
public class UserLoyaltyLedger {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "payment_transaction_id", columnDefinition = "uuid", nullable = false, unique = true)
    private UUID paymentTransactionId;

    @Column(name = "booking_id", columnDefinition = "uuid", nullable = false)
    private UUID bookingId;

    @Column(name = "user_id", columnDefinition = "uuid", nullable = false)
    private UUID userId;

    @Column(name = "loyalty_points_used", nullable = false)
    private Long loyaltyPointsUsed;

    @Column(name = "loyalty_points_earned", nullable = false)
    private Long loyaltyPointsEarned;

    @Column(name = "source", nullable = false, length = 60)
    private String source;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    @Column(name = "time_created", nullable = false, updatable = false)
    private LocalDateTime timeCreated;

    @Column(name = "time_updated", nullable = false)
    private LocalDateTime timeUpdated;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UuidCreator.getTimeOrderedEpoch();
        }
        LocalDateTime now = LocalDateTime.now();
        if (occurredAt == null) {
            occurredAt = now;
        }
        if (processedAt == null) {
            processedAt = now;
        }
        timeCreated = now;
        timeUpdated = now;
    }

    @PreUpdate
    public void preUpdate() {
        timeUpdated = LocalDateTime.now();
    }
}

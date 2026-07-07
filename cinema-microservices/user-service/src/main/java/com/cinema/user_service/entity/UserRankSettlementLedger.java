package com.cinema.user_service.entity;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_rank_settlement_ledger",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_rank_settlement_transaction_type",
                columnNames = {"payment_transaction_id", "settlement_type"}))
@Getter
@Setter
public class UserRankSettlementLedger {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "payment_transaction_id", columnDefinition = "uuid", nullable = false)
    private UUID paymentTransactionId;

    @Column(name = "booking_id", columnDefinition = "uuid", nullable = false)
    private UUID bookingId;

    @Column(name = "user_id", columnDefinition = "uuid", nullable = false)
    private UUID userId;

    @Column(name = "settlement_type", nullable = false, length = 30)
    private String settlementType;

    @Column(name = "settlement_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal settlementAmount;

    @Column(name = "lifetime_amount_before", nullable = false, precision = 14, scale = 2)
    private BigDecimal lifetimeAmountBefore;

    @Column(name = "lifetime_amount_after", nullable = false, precision = 14, scale = 2)
    private BigDecimal lifetimeAmountAfter;

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

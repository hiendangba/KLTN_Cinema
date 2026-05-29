package com.cinema.payment_service.entity;

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
@Table(name = "payment_transaction_promotion",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_payment_transaction_promotion_order",
                columnNames = {"payment_transaction_id", "apply_order"}))
@Getter
@Setter
public class PaymentTransactionPromotion {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "payment_transaction_id", columnDefinition = "uuid", nullable = false)
    private UUID paymentTransactionId;

    @Column(name = "promotion_id", columnDefinition = "uuid")
    private UUID promotionId;

    @Column(name = "promotion_code", nullable = false, length = 80)
    private String promotionCode;

    @Column(name = "promotion_name", nullable = false, length = 120)
    private String promotionName;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "apply_order", nullable = false)
    private Integer applyOrder;

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
        timeCreated = now;
        timeUpdated = now;
    }

    @PreUpdate
    public void preUpdate() {
        timeUpdated = LocalDateTime.now();
    }
}

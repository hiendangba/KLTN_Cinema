package com.cinema.user_service.entity;

import com.cinema.user_service.enums.CustomerRankStatus;
import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "customer_rank")
@Getter
@Setter
public class CustomerRank {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "min_lifetime_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal minLifetimeAmount;

    @Column(name = "earning_amount_unit", nullable = false, precision = 14, scale = 2)
    private BigDecimal earningAmountUnit;

    @Column(name = "earning_points_per_unit", nullable = false, precision = 8, scale = 2)
    private BigDecimal earningPointsPerUnit;

    @Column(name = "rank_level", nullable = false, unique = true)
    private Integer level;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CustomerRankStatus status;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted;

    @Column(name = "time_created", nullable = false, updatable = false)
    private LocalDateTime timeCreated;

    @Column(name = "time_updated", nullable = false)
    private LocalDateTime timeUpdated;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UuidCreator.getTimeOrderedEpoch();
        }
        if (status == null) {
            status = CustomerRankStatus.ACTIVE;
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

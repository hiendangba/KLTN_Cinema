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

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "promotion_cinema",
        uniqueConstraints = @UniqueConstraint(name = "uk_promotion_cinema",
                columnNames = {"promotion_id", "cinema_id"}))
@Getter
@Setter
public class PromotionCinema {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "promotion_id", nullable = false, columnDefinition = "uuid")
    private UUID promotionId;

    @Column(name = "cinema_id", nullable = false, columnDefinition = "uuid")
    private UUID cinemaId;

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

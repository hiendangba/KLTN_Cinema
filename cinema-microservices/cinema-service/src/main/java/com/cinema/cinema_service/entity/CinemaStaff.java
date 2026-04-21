package com.cinema.cinema_service.entity;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "cinema_staff",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_cinema_staff_staff_id", columnNames = {"staff_id"})
        },
        indexes = {
                @Index(name = "idx_cinema_staff_cinema_id", columnList = "cinema_id"),
                @Index(name = "idx_cinema_staff_active", columnList = "active")
        })
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class CinemaStaff {
    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "cinema_id", columnDefinition = "uuid", nullable = false)
    private UUID cinemaId;

    @Column(name = "staff_id", columnDefinition = "uuid", nullable = false)
    private UUID staffId;

    @Column(nullable = false)
    private Boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void prePersist() {
        if (id == null) {
            id = UuidCreator.getTimeOrderedEpoch();
        }
        if (active == null) {
            active = true;
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

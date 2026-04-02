package com.cinema.showtime_service.entity;

import jakarta.persistence.*;
import lombok.*;
import com.github.f4b6a3.uuid.UuidCreator;

import java.time.LocalDateTime;
import java.util.UUID;

import com.cinema.Enum.ShowTimeEnum;

@Entity
@Table(name = "show_time")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ShowTime {
    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false)
    private LocalDateTime startDateTime;

    @Column(nullable = false)
    private LocalDateTime endDateTime;

    @Column(name = "hall_id", columnDefinition = "uuid", nullable = false)
    private UUID hallId;

    @Column(name = "film_id", columnDefinition = "uuid", nullable = false)
    private UUID filmId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ShowTimeEnum.ShowTimeStatus status;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted;

    @Column(name = "time_created", nullable = false, updatable = false)
    private LocalDateTime timeCreated;

    @Column(name = "time_updated", nullable = false)
    private LocalDateTime timeUpdated;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidCreator.getTimeOrderedEpoch(); // UUIDv7
        }
        isDeleted = false;
        timeCreated = LocalDateTime.now();
        timeUpdated = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        timeUpdated = LocalDateTime.now();
    }
}

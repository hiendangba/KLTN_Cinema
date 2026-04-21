package com.cinema.hall_service.entity;

import com.cinema.Enum.HallEnum;
import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "hall")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Hall {
    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "cinema_id", columnDefinition = "uuid", nullable = false)
    private UUID cinemaId;

    @Column(nullable = false)
    private String name;

    @Column(name = "layout_json", columnDefinition = "text", nullable = false)
    private String layoutJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HallEnum.HallStatus status;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted;

    @Column(name = "time_created", nullable = false, updatable = false)
    private LocalDateTime timeCreated;

    @Column(name = "time_updated", nullable = false)
    private LocalDateTime timeUpdated;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidCreator.getTimeOrderedEpoch();
        }
        if (this.status == null) {
            this.status = HallEnum.HallStatus.ACTIVE;
        }
        this.isDeleted = false;
        this.timeCreated = LocalDateTime.now();
        this.timeUpdated = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.timeUpdated = LocalDateTime.now();
    }
}

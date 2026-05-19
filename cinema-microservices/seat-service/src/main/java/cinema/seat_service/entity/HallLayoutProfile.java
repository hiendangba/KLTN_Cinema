package cinema.seat_service.entity;

import cinema.seat_service.enums.ScreenPosition;
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

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "hall_layout_profile")
@Getter
@Setter
public class HallLayoutProfile {

    @Id
    @Column(name = "hall_id", columnDefinition = "uuid")
    private UUID hallId;

    @Column(name = "total_rows", nullable = false)
    private Integer totalRows;

    @Column(name = "total_cols", nullable = false)
    private Integer totalCols;

    @Enumerated(EnumType.STRING)
    @Column(name = "screen_position", nullable = false, length = 20)
    private ScreenPosition screenPosition;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted;

    @Column(name = "time_created", nullable = false, updatable = false)
    private LocalDateTime timeCreated;

    @Column(name = "time_updated", nullable = false)
    private LocalDateTime timeUpdated;

    @PrePersist
    public void prePersist() {
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

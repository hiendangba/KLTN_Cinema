package cinema.seat_service.entity;

import cinema.seat_service.enums.SeatType;
import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "seat", uniqueConstraints = {
        @UniqueConstraint(name = "uk_seat_hall_code", columnNames = {"hall_id", "seat_code"}),
        @UniqueConstraint(name = "uk_seat_hall_row_col", columnNames = {"hall_id", "seat_row", "seat_col"})
})
@Getter
@Setter
public class Seat {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "hall_id", columnDefinition = "uuid", nullable = false)
    private UUID hallId;

    @Column(name = "seat_code", nullable = false, length = 20)
    private String seatCode;

    @Column(name = "seat_row", nullable = false)
    private Integer row;

    @Column(name = "seat_col", nullable = false)
    private Integer col;

    @Enumerated(EnumType.STRING)
    @Column(name = "seat_type", nullable = false, length = 20)
    private SeatType seatType;

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

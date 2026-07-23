package com.cinema.identity_service.entity;

import jakarta.persistence.*;
import com.github.f4b6a3.uuid.UuidCreator;
import lombok.*;
import com.cinema.Enum.UserEnum;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "identity", indexes = {
        @Index(name = "idx_email", columnList = "email"),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_provider", columnList = "providerId, provider")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;
    @Column(nullable = false, unique = true, length = 255)
    private String email;
    @Column(nullable = false, length = 255)
    private String password;
    @Column(name = "provider_id", length = 255)
    private String providerId;
    @Column(length = 50)
    private String provider; // GOOGLE, FACEBOOK, etc.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserEnum.UserRole role;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserEnum.UserStatus status;
    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted;
    @Column(nullable = false, updatable = false)
    private LocalDateTime timeCreated;

    @Column(nullable = false)
    private LocalDateTime timeUpdated;

    @PrePersist
    public void generateId() {
        if (this.id == null) {
            this.id = UuidCreator.getTimeOrderedEpoch();
        }
        if (this.isDeleted == null) {
            this.isDeleted = false;
        }
        timeCreated = LocalDateTime.now();
        timeUpdated = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        timeUpdated = LocalDateTime.now();
    }
}

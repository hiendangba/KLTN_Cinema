package com.cinema.identity_service.entity;

import jakarta.persistence.*;
import com.github.f4b6a3.uuid.UuidCreator;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;


@Entity
@Table(
        name = "users",
        indexes = {
                @Index(name = "idx_email", columnList = "email"),  // ✅ Index cho email
                @Index(name = "idx_role_status", columnList = "role, status"),  // Composite index
                @Index(name = "idx_status", columnList = "status"),
                @Index(name = "idx_provider_id", columnList = "providerId, provider")  // OAuth lookup
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_email", columnNames = "email")  // Unique constraint
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(name = "dob")
    private LocalDate dob;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Gender gender;

    @Column(name = "provider_id", length = 255)
    private String providerId;

    @Column(length = 50)
    private String provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "bank_code", length = 50)
    private String bankCode;

    @Column(name = "account_number", length = 50)
    private String accountNumber;

    @Column(name = "account_name", length = 255)
    private String accountName;

    @Column(nullable = false, updatable = false)
    private LocalDateTime timeCreated;

    @Column(nullable = false)
    private LocalDateTime timeUpdated;

    @PrePersist
    public void generateId() {
        if (this.id == null) {
            this.id = UuidCreator.getTimeOrderedEpoch();
        }
        timeCreated = LocalDateTime.now();
        timeUpdated = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        timeUpdated = LocalDateTime.now();
    }

    public enum Gender {
        MALE,
        FEMALE,
        OTHER
    }

    public enum UserRole {
        ADMIN,
        OWNER,
        STAFF,
        CUSTOMER
    }

    public enum UserStatus {
        ACTIVE,
        LOCKED
    }
}

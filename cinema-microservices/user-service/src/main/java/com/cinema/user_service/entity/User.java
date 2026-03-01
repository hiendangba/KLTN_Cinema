package com.cinema.user_service.entity;
import com.cinema.Enum.UserEnum;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import java.time.LocalDateTime;
import java.util.UUID;
import java.time.LocalDate;

@Entity
@Table(
        name = "users",
        indexes = {
                @Index(name = "idx_phone", columnList = "phone"),
                @Index(name = "idx_name", columnList = "name")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class User {
    @Id
    @Column(columnDefinition = "uuid")
    UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, length = 100)
    String name;

    @Column(name = "dob")
    LocalDate dob;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    UserEnum.Gender gender;

    @Column(length = 15)
    String phone;

    @Column(name = "bank_code", length = 50)
    String bankCode;

    @Column(name = "account_number", length = 50)
    String accountNumber;

    @Column(name = "account_name")
    String accountName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserEnum.UserRole role;

    @Column(nullable = false, updatable = false)
    LocalDateTime timeCreated;

    @Column(nullable = false)
    LocalDateTime timeUpdated;

    @PrePersist
    protected void onCreate() {
        timeCreated = LocalDateTime.now();
        timeUpdated = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        timeUpdated = LocalDateTime.now();
    }
}

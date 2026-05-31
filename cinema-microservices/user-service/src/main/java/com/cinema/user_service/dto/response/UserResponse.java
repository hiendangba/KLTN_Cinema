package com.cinema.user_service.dto.response;

import com.cinema.Enum.UserEnum;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserResponse {
    UUID id;
    String email;
    String name;
    LocalDate dob;
    UserEnum.Gender gender;
    String phone;
    UserEnum.UserRole role;
    LocalDateTime timeCreated;
    LocalDateTime timeUpdated;
    String bankCode;
    String accountNumber;
    String accountName;
    IdentityAccountResponse identityAccount;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class IdentityAccountResponse {
        UUID id;
        String email;
        String provider;
        String providerId;
        String role;
        String status;
        Boolean isDeleted;
        LocalDateTime timeCreated;
        LocalDateTime timeUpdated;
    }
}

package com.cinema.cinema_service.dto.response;

import com.cinema.cinema_service.enums.CinemaStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CinemaResponse {
    UUID id;
    String code;
    String name;
    String address;
    BigDecimal latitude;
    BigDecimal longitude;
    String phone;
    LocalTime openTime;
    LocalTime closeTime;
    CinemaStatus status;
    UUID managerId;
    Boolean isDeleted;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
    List<UUID> staffIds;
}

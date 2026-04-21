package com.cinema.cinema_service.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CinemaStaffResponse {
    UUID id;
    UUID cinemaId;
    UUID staffId;
    Boolean active;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}

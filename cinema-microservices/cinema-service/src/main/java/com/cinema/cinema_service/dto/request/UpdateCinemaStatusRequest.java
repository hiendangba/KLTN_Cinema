package com.cinema.cinema_service.dto.request;

import com.cinema.cinema_service.enums.CinemaStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateCinemaStatusRequest {
    @NotNull(message = "Status is required")
    CinemaStatus status;
}

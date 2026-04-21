package com.cinema.hall_service.dto.request;

import com.cinema.Enum.HallEnum;
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
public class UpdateHallStatusRequest {
    @NotNull(message = "Hall status is required")
    HallEnum.HallStatus status;
}


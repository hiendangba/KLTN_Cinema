package com.cinema.hall_service.dto.request;

import com.cinema.Enum.HallEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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
public class SeatUpsertRequest {
    @NotBlank(message = "Seat code is required")
    @Size(max = 20, message = "Seat code must be at most 20 characters")
    String seatCode;

    @NotNull(message = "Row is required")
    @Positive(message = "Row must be greater than 0")
    Integer row;

    @NotNull(message = "Column is required")
    @Positive(message = "Column must be greater than 0")
    Integer col;

    @NotNull(message = "Seat type is required")
    HallEnum.SeatType seatType;
}

package com.cinema.showtime_service.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatSuggestionRequest {
    @NotNull(message = "seatCount is required")
    @Min(value = 1, message = "seatCount must be at least 1")
    @Max(value = 5, message = "seatCount must not exceed 5")
    private Integer seatCount;

    @NotNull(message = "preferCoupleSeat is required")
    private Boolean preferCoupleSeat;
}

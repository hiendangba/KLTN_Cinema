package com.cinema.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DateRange {

    @NotNull(message = "From date is required")
    private LocalDateTime from;

    @NotNull(message = "To date is required")
    private LocalDateTime to;
}

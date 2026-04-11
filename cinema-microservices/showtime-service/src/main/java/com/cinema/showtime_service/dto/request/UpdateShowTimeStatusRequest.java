package com.cinema.showtime_service.dto.request;

import lombok.Data;
import lombok.NoArgsConstructor;
import com.cinema.Enum.ShowTimeEnum;
import lombok.AllArgsConstructor;
import jakarta.validation.constraints.NotNull;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateShowTimeStatusRequest {
    @NotNull(message = "Showtime status is required")
    private ShowTimeEnum.ShowTimeStatus status;
}

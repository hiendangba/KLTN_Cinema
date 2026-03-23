package com.cinema.showtime_service.dto.request;

import lombok.Data;
import lombok.NoArgsConstructor;
import com.cinema.Enum.ShowTimeEnum;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateShowTimeStatusRequest {
    private ShowTimeEnum.ShowTimeStatus status;
}

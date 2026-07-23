package com.cinema.hall_service.dto.response;

import com.cinema.Enum.HallEnum;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SeatResponse {
    UUID id;
    String seatCode;
    Integer row;
    Integer col;
    HallEnum.SeatType seatType;
}

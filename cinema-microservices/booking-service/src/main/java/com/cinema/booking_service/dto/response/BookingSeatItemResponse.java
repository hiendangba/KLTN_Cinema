package com.cinema.booking_service.dto.response;

import com.cinema.Enum.HallEnum;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Builder
public class BookingSeatItemResponse {
    private UUID id;
    private String seatCode;
    private HallEnum.SeatType seatType;
    private BigDecimal seatPriceSnapshot;
}


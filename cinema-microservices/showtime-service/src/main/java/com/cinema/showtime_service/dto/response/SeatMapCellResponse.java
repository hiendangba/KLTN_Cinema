package com.cinema.showtime_service.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SeatMapCellResponse {
    private Integer row;
    private Integer col;
    private String kind;
    private String seatCode;
    private String seatType;
    private String cellType;
    private String state;
    private Long price;
}

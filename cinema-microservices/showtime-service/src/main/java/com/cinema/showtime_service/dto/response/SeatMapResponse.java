package com.cinema.showtime_service.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class SeatMapResponse {
    private UUID showtimeId;
    private UUID hallId;
    private Integer totalRows;
    private Integer totalCols;
    private String screenPosition;
    @Builder.Default
    private List<SeatMapCellResponse> cells = new ArrayList<>();
}

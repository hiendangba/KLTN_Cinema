package com.cinema.showtime_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatSuggestionCandidateResponse {
    @Builder.Default
    private List<String> seatCodes = new ArrayList<>();
    private Integer seatCount;
    private Boolean hasCoupleSeats;
    private Long totalPrice;
    private Integer score;
    private String reason;
}

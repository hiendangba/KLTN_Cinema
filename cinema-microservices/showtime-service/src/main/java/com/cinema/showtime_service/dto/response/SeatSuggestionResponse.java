package com.cinema.showtime_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatSuggestionResponse {
    private UUID showtimeId;
    private Integer requestedSeatCount;
    private Boolean preferCoupleSeat;
    @Builder.Default
    private List<SeatSuggestionCandidateResponse> candidates = new ArrayList<>();
}

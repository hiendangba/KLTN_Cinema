package com.cinema.showtime_service.services;

import com.cinema.showtime_service.dto.request.SeatSuggestionRequest;
import com.cinema.showtime_service.dto.response.SeatSuggestionResponse;

import java.util.UUID;

public interface SeatSuggestionService {
    SeatSuggestionResponse suggestSeatSuggestions(UUID showtimeId, SeatSuggestionRequest request);
}

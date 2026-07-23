package com.cinema.showtime_service.controller;

import com.cinema.Enum.SuccessMessage;
import com.cinema.controller.BaseController;
import com.cinema.dto.request.PageRequest;
import com.cinema.dto.response.APIResponse;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.dto.response.PageResponse;
import com.cinema.showtime_service.dto.request.ShowTimeCreateRequest;
import com.cinema.showtime_service.dto.request.SeatSuggestionRequest;
import com.cinema.showtime_service.dto.request.SearchShowtimesByFilmRequest;
import com.cinema.showtime_service.dto.request.UpdateShowTimeRequest;
import com.cinema.showtime_service.dto.response.SeatMapResponse;
import com.cinema.showtime_service.dto.response.SeatSuggestionResponse;
import com.cinema.showtime_service.dto.response.ShowTimeResponse;
import com.cinema.showtime_service.services.SeatSuggestionService;
import com.cinema.showtime_service.services.ShowTimeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/showtimes")
@Slf4j
@RequiredArgsConstructor
public class ShowTimeController extends BaseController {
    private final ShowTimeService showTimeService;
    private final SeatSuggestionService seatSuggestionService;

    @PostMapping("/search")
    public ResponseEntity<APIResponse<PageResponse<ShowTimeResponse>>> searchShowtimes(
            @Valid @RequestBody PageRequest<com.cinema.showtime_service.dto.request.ShowTimeField> request,
            HttpServletRequest httpRequest) {
        PageResponse<ShowTimeResponse> response = showTimeService.searchShowtimes(request, httpRequest);
        return ok(SuccessMessage.SHOWTIMES_SEARCHED, response);
    }

    @PostMapping
    public ResponseEntity<APIResponse<ActionMessageResponse>> createShowTime(
            @Valid @RequestBody ShowTimeCreateRequest showTimeCreateRequest, HttpServletRequest httpRequest) {
        log.info("Request tao showtime cho phim: {}", showTimeCreateRequest);
        ActionMessageResponse response = showTimeService.createShowTime(showTimeCreateRequest, httpRequest);
        return created(SuccessMessage.SHOWTIME_CREATED, response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<APIResponse<ActionMessageResponse>> updateShowTime(
        @Valid @PathVariable UUID id,
        @Valid @RequestBody UpdateShowTimeRequest updateShowTimeRequest,
        HttpServletRequest httpRequest) {
        ActionMessageResponse response = showTimeService.updateShowTime(id, updateShowTimeRequest, httpRequest);
        return ok(SuccessMessage.SHOWTIME_UPDATED, response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<APIResponse<ActionMessageResponse>> deleteShowTime(
            @PathVariable UUID id,
            HttpServletRequest httpRequest) {
        ActionMessageResponse response = showTimeService.deleteShowTime(id, httpRequest);
        return ok(SuccessMessage.SHOWTIME_DELETED, response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<APIResponse<ShowTimeResponse>> getShowTimeById(@PathVariable UUID id) {
        ShowTimeResponse response = showTimeService.getShowTimeById(id);
        return ok(SuccessMessage.SHOWTIME_FETCHED, response);
    }

    @GetMapping("/{id}/seat-map")
    public ResponseEntity<APIResponse<SeatMapResponse>> getSeatMap(@PathVariable UUID id) {
        SeatMapResponse response = showTimeService.getSeatMapByShowtimeId(id);
        return ok(SuccessMessage.SHOWTIME_FETCHED, response);
    }

    @PostMapping("/{showtimeId}/seat-suggestions")
    public ResponseEntity<APIResponse<SeatSuggestionResponse>> getSeatSuggestions(
            @PathVariable UUID showtimeId,
            @Valid @RequestBody SeatSuggestionRequest request) {
        SeatSuggestionResponse response = seatSuggestionService.suggestSeatSuggestions(showtimeId, request);
        return ok(SuccessMessage.SEAT_SUGGESTIONS_FETCHED, response);
    }

    @PostMapping("/films/{filmId}/search")
    public ResponseEntity<APIResponse<PageResponse<ShowTimeResponse>>> searchShowtimesByFilmId(
            @PathVariable UUID filmId,
            @Valid @RequestBody SearchShowtimesByFilmRequest request) {
        PageResponse<ShowTimeResponse> response = showTimeService.searchShowtimesByFilmId(filmId, request);
        return ok(SuccessMessage.SHOWTIMES_SEARCHED, response);
    }
}

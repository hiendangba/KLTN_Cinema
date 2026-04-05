
package com.cinema.showtime_service.controller;

import com.cinema.controller.BaseController;
import com.cinema.dto.request.CursorPageRequest;
import com.cinema.dto.response.APIResponse;
import com.cinema.dto.response.CursorPageResponse;
import com.cinema.dto.response.ResultResponse;
import com.cinema.showtime_service.dto.request.ShowTimeCreateRequest;
import com.cinema.showtime_service.dto.response.ShowTimeResponse;
import com.cinema.showtime_service.dto.response.ShowTimeWithFilmResponse;
import com.cinema.showtime_service.services.ShowTimeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.cinema.showtime_service.dto.request.UpdateShowTimeStatusRequest;

import java.util.UUID;

@RestController
@RequestMapping("/api/showtimes")
@Slf4j
@RequiredArgsConstructor
public class ShowTimeController extends BaseController {
    private final ShowTimeService showTimeService;

    @PostMapping("/search")
    public ResponseEntity<APIResponse<CursorPageResponse<ShowTimeResponse>>> searchShowtimes(
            @RequestBody CursorPageRequest<com.cinema.showtime_service.dto.request.ShowTimeField> request) {
        CursorPageResponse<ShowTimeResponse> response = showTimeService.searchShowtimes(request);
        return ok(response);
    }

    @PostMapping("/search-with-film")
    public ResponseEntity<APIResponse<CursorPageResponse<ShowTimeWithFilmResponse>>> searchShowtimesWithFilm(
            @RequestBody CursorPageRequest<com.cinema.showtime_service.dto.request.ShowTimeField> request) {
        CursorPageResponse<ShowTimeWithFilmResponse> response = showTimeService.searchShowtimesWithFilm(request);
        return ok(response);
    }

    @PostMapping
    public ResponseEntity<APIResponse<ResultResponse<ShowTimeResponse>>> createShowTime(
            @Valid @RequestBody ShowTimeCreateRequest showTimeCreateRequest, HttpServletRequest httpRequest) {
        log.info("Request tạo showtime cho phim: {}", showTimeCreateRequest);
        ResultResponse<ShowTimeResponse> response = showTimeService.createShowTime(showTimeCreateRequest,
                httpRequest);
        return created(response);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<APIResponse<ShowTimeResponse>> updateShowTimeStatus(
            @Valid @PathVariable UUID id,
            @RequestBody UpdateShowTimeStatusRequest updateShowTimeStatusRequest,
            HttpServletRequest httpRequest) {
        ShowTimeResponse response = showTimeService.updateShowTimeStatus(id, updateShowTimeStatusRequest, httpRequest);
        return ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<APIResponse<Void>> deleteShowTime(@PathVariable UUID id, HttpServletRequest httpRequest) {
        showTimeService.deleteShowTime(id, httpRequest);
        return ok(null);
    }

    @GetMapping("/{id}")
    public ResponseEntity<APIResponse<ShowTimeResponse>> getShowTimeById(@PathVariable UUID id) {
        ShowTimeResponse response = showTimeService.getShowTimeById(id);
        return ok(response);
    }

    @GetMapping("/{id}/with-film")
    public ResponseEntity<APIResponse<ShowTimeWithFilmResponse>> getShowTimeByIdWithFilm(@PathVariable UUID id) {
        ShowTimeWithFilmResponse response = showTimeService.getShowTimeByIdWithFilm(id);
        return ok(response);
    }
}

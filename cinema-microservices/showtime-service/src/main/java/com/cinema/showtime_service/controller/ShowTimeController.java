package com.cinema.showtime_service.controller;

import com.cinema.controller.BaseController;
import com.cinema.dto.request.CursorPageRequest;
import com.cinema.dto.response.APIResponse;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.dto.response.CursorPageResponse;
import com.cinema.dto.response.ResultResponse;
import com.cinema.showtime_service.dto.request.ShowTimeCreateRequest;
import com.cinema.showtime_service.dto.request.UpdateShowTimeRequest;
import com.cinema.showtime_service.dto.request.UpdateShowTimeStatusRequest;
import com.cinema.showtime_service.dto.response.ShowTimeResponse;
import com.cinema.showtime_service.services.ShowTimeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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

    @PostMapping("/search")
    public ResponseEntity<APIResponse<CursorPageResponse<ShowTimeResponse>>> searchShowtimes(
            @Valid @RequestBody CursorPageRequest<com.cinema.showtime_service.dto.request.ShowTimeField> request) {
        CursorPageResponse<ShowTimeResponse> response = showTimeService.searchShowtimes(request);
        return ok(response);
    }

    @PostMapping
    public ResponseEntity<APIResponse<ResultResponse<ShowTimeResponse>>> createShowTime(
            @Valid @RequestBody ShowTimeCreateRequest showTimeCreateRequest, HttpServletRequest httpRequest) {
        log.info("Request tao showtime cho phim: {}", showTimeCreateRequest);
        ResultResponse<ShowTimeResponse> response = showTimeService.createShowTime(showTimeCreateRequest, httpRequest);
        return created(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<APIResponse<ActionMessageResponse>> updateShowTime(
            @Valid @PathVariable UUID id,
            @Valid @RequestBody UpdateShowTimeRequest updateShowTimeRequest,
            HttpServletRequest httpRequest) {
        ActionMessageResponse response = showTimeService.updateShowTime(id, updateShowTimeRequest, httpRequest);
        return ok(response);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<APIResponse<ActionMessageResponse>> updateShowTimeStatus(
            @Valid @PathVariable UUID id,
            @Valid @RequestBody UpdateShowTimeStatusRequest updateShowTimeStatusRequest,
            HttpServletRequest httpRequest) {
        ActionMessageResponse response = showTimeService.updateShowTimeStatus(id, updateShowTimeStatusRequest,
                httpRequest);
        return ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<APIResponse<ActionMessageResponse>> deleteShowTime(
            @PathVariable UUID id,
            HttpServletRequest httpRequest) {
        ActionMessageResponse response = showTimeService.deleteShowTime(id, httpRequest);
        return ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<APIResponse<ShowTimeResponse>> getShowTimeById(@PathVariable UUID id) {
        ShowTimeResponse response = showTimeService.getShowTimeById(id);
        return ok(response);
    }
}

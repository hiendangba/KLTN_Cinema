package com.cinema.showtime_service.controller;

import com.cinema.controller.BaseController;
import com.cinema.dto.response.APIResponse;
import com.cinema.dto.response.ResultResponse;
import com.cinema.showtime_service.dto.request.ShowTimeCreateRequest;
import com.cinema.showtime_service.dto.response.ShowTimeResponse;
import com.cinema.showtime_service.services.ShowTimeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/showtimes")
@Slf4j
@RequiredArgsConstructor
public class ShowTimeController extends BaseController {
    private final ShowTimeService showTimeService;

    @PostMapping
    public ResponseEntity<APIResponse<ResultResponse<ShowTimeResponse>>> createShowTime(
            @Valid @RequestBody ShowTimeCreateRequest showTimeCreateRequest, HttpServletRequest httpRequest) {
        log.info("Request tạo showtime cho phim: {}", showTimeCreateRequest);
        ResultResponse<ShowTimeResponse> response = showTimeService.createShowTime(showTimeCreateRequest,
                httpRequest);
        return created(response);
    }
}

package com.cinema.showtime_service.services;

import com.cinema.dto.response.ResultResponse;
import com.cinema.showtime_service.dto.request.ShowTimeCreateRequest;
import com.cinema.showtime_service.dto.response.ShowTimeResponse;
import jakarta.servlet.http.HttpServletRequest;

public interface ShowTimeService {
    ResultResponse<ShowTimeResponse> createShowTime(ShowTimeCreateRequest showTimeCreateRequest, HttpServletRequest httpRequest);
}

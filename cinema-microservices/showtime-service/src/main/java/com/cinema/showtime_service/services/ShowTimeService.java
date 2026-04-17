package com.cinema.showtime_service.services;

import com.cinema.dto.request.CursorPageRequest;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.dto.response.CursorPageResponse;
import com.cinema.dto.response.ResultResponse;
import com.cinema.showtime_service.dto.request.ShowTimeCreateRequest;
import com.cinema.showtime_service.dto.request.ShowTimeField;
import com.cinema.showtime_service.dto.request.UpdateShowTimeRequest;
import com.cinema.showtime_service.dto.request.UpdateShowTimeStatusRequest;
import com.cinema.showtime_service.dto.response.ShowTimeResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

public interface ShowTimeService {
    ResultResponse<ShowTimeResponse> createShowTime(ShowTimeCreateRequest showTimeCreateRequest,
                                                    HttpServletRequest httpRequest);

    ActionMessageResponse updateShowTime(UUID id, UpdateShowTimeRequest updateShowTimeRequest,
                                         HttpServletRequest httpRequest);

    ActionMessageResponse updateShowTimeStatus(UUID id, UpdateShowTimeStatusRequest updateShowTimeStatusRequest,
                                               HttpServletRequest httpRequest);

    ActionMessageResponse deleteShowTime(UUID id, HttpServletRequest httpRequest);

    CursorPageResponse<ShowTimeResponse> searchShowtimes(
            CursorPageRequest<ShowTimeField> request);

    ShowTimeResponse getShowTimeById(UUID id);
}

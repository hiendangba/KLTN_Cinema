package com.cinema.showtime_service.services;

import com.cinema.dto.request.PageRequest;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.dto.response.PageResponse;
import com.cinema.dto.response.ResultResponse;
import com.cinema.showtime_service.dto.request.SearchShowtimesByFilmRequest;
import com.cinema.showtime_service.dto.request.ShowTimeCreateRequest;
import com.cinema.showtime_service.dto.request.ShowTimeField;
import com.cinema.showtime_service.dto.request.UpdateShowTimeRequest;
import com.cinema.showtime_service.dto.response.SeatMapResponse;
import com.cinema.showtime_service.dto.response.ShowTimeResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDateTime;
import java.util.UUID;

public interface ShowTimeService {
    ResultResponse<ShowTimeResponse> createShowTime(ShowTimeCreateRequest showTimeCreateRequest,
                                                    HttpServletRequest httpRequest);

    ActionMessageResponse updateShowTime(UUID id, UpdateShowTimeRequest updateShowTimeRequest,
                                         HttpServletRequest httpRequest);

    ActionMessageResponse deleteShowTime(UUID id, HttpServletRequest httpRequest);

    PageResponse<ShowTimeResponse> searchShowtimes(
            PageRequest<ShowTimeField> request,
            HttpServletRequest httpRequest);

    ShowTimeResponse getShowTimeById(UUID id);

    SeatMapResponse getSeatMapByShowtimeId(UUID showtimeId);

    PageResponse<ShowTimeResponse> searchShowtimesByFilmId(UUID filmId, SearchShowtimesByFilmRequest request);

    int promoteScheduledShowtimesToOngoing(LocalDateTime now, int windowDays);

    int expireOngoingShowtimes(LocalDateTime now);
}

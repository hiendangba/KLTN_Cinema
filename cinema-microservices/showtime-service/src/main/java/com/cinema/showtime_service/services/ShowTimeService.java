package com.cinema.showtime_service.services;

import com.cinema.dto.request.CursorPageRequest;
import com.cinema.dto.response.CursorPageResponse;
import com.cinema.dto.response.ResultResponse;
import com.cinema.showtime_service.dto.request.ShowTimeCreateRequest;
import com.cinema.showtime_service.dto.request.ShowTimeField;
import com.cinema.showtime_service.dto.request.UpdateShowTimeStatusRequest;
import com.cinema.showtime_service.dto.response.ShowTimeResponse;
import com.cinema.showtime_service.dto.response.ShowTimeWithFilmResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

public interface ShowTimeService {
    ResultResponse<ShowTimeResponse> createShowTime(ShowTimeCreateRequest showTimeCreateRequest,
                                                    HttpServletRequest httpRequest);

    ShowTimeResponse updateShowTimeStatus(UUID id, UpdateShowTimeStatusRequest updateShowTimeStatusRequest,
                                          HttpServletRequest httpRequest);

    void deleteShowTime(UUID id, HttpServletRequest httpRequest);

    // API search showtime phân trang cursor, filter/sort động
    CursorPageResponse<ShowTimeResponse> searchShowtimes(
            CursorPageRequest<ShowTimeField> request);

    ShowTimeResponse getShowTimeById(UUID id);

    CursorPageResponse<ShowTimeWithFilmResponse> searchShowtimesWithFilm(
            CursorPageRequest<ShowTimeField> request);

    ShowTimeWithFilmResponse getShowTimeByIdWithFilm(UUID id);
}

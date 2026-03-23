package com.cinema.showtime_service.services;

import com.cinema.dto.request.CursorPageRequest;
import com.cinema.dto.response.CursorPageResponse;
import com.cinema.dto.response.ResultResponse;
import com.cinema.showtime_service.dto.request.ShowTimeCreateRequest;
import com.cinema.showtime_service.dto.request.ShowTimeSortField;
import com.cinema.showtime_service.dto.request.UpdateShowTimeStatusRequest;
import com.cinema.showtime_service.dto.response.ShowTimeResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

public interface ShowTimeService {
    ResultResponse<ShowTimeResponse> createShowTime(ShowTimeCreateRequest showTimeCreateRequest,
                                                    HttpServletRequest httpRequest);

    ShowTimeResponse updateShowTimeStatus(UUID id, UpdateShowTimeStatusRequest updateShowTimeStatusRequest,
                                          HttpServletRequest httpRequest);

    void deleteShowTime(UUID id, HttpServletRequest httpRequest);

    // API lấy danh sách showtime phân trang cursor, lọc trạng thái, isDeleted, sort
    // đa trường
    CursorPageResponse<ShowTimeResponse> getAllShowtimes(CursorPageRequest<ShowTimeSortField> request);
}

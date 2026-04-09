package com.cinema.hall_services.services;

import com.cinema.dto.request.CursorPageRequest;
import com.cinema.dto.response.CursorPageResponse;
import com.cinema.hall_services.dto.request.HallCreateRequest;
import com.cinema.hall_services.dto.request.HallField;
import com.cinema.hall_services.dto.request.UpdateHallLayoutRequest;
import com.cinema.hall_services.dto.response.HallResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

public interface HallService {
    HallResponse createHall(HallCreateRequest request, HttpServletRequest httpRequest);

    HallResponse getHallById(UUID id);

    CursorPageResponse<HallResponse> searchHalls(CursorPageRequest<HallField> request);

    HallResponse updateHallLayout(UUID hallId, UpdateHallLayoutRequest request, HttpServletRequest httpRequest);
}

package com.cinema.hall_services.services;

import com.cinema.dto.request.CursorPageRequest;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.dto.response.CursorPageResponse;
import com.cinema.hall_services.dto.request.HallCreateRequest;
import com.cinema.hall_services.dto.request.HallField;
import com.cinema.hall_services.dto.request.UpdateHallRequest;
import com.cinema.hall_services.dto.request.UpdateHallLayoutRequest;
import com.cinema.hall_services.dto.request.UpdateHallStatusRequest;
import com.cinema.hall_services.dto.response.HallResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

public interface HallService {
    ActionMessageResponse createHall(HallCreateRequest request, HttpServletRequest httpRequest);

    HallResponse getHallById(UUID id);

    CursorPageResponse<HallResponse> searchHalls(CursorPageRequest<HallField> request);

    ActionMessageResponse updateHall(UUID hallId, UpdateHallRequest request, HttpServletRequest httpRequest);

    ActionMessageResponse updateHallStatus(UUID hallId, UpdateHallStatusRequest request, HttpServletRequest httpRequest);

    ActionMessageResponse updateHallLayout(UUID hallId, UpdateHallLayoutRequest request, HttpServletRequest httpRequest);

    ActionMessageResponse deleteHall(UUID hallId, HttpServletRequest httpRequest);
}

package com.cinema.hall_service.services;

import com.cinema.dto.request.PageRequest;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.dto.response.PageResponse;
import com.cinema.hall_service.dto.request.HallCreateRequest;
import com.cinema.hall_service.dto.request.HallField;
import com.cinema.hall_service.dto.request.UpdateHallRequest;
import com.cinema.hall_service.dto.request.UpdateHallLayoutRequest;
import com.cinema.hall_service.dto.request.UpdateHallStatusRequest;
import com.cinema.hall_service.dto.response.HallResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

public interface HallService {
    ActionMessageResponse createHall(HallCreateRequest request, HttpServletRequest httpRequest);

    HallResponse getHallById(UUID id);

    PageResponse<HallResponse> searchHalls(PageRequest<HallField> request);

    ActionMessageResponse updateHall(UUID hallId, UpdateHallRequest request, HttpServletRequest httpRequest);

    ActionMessageResponse updateHallStatus(UUID hallId, UpdateHallStatusRequest request, HttpServletRequest httpRequest);

    ActionMessageResponse updateHallLayout(UUID hallId, UpdateHallLayoutRequest request, HttpServletRequest httpRequest);

    ActionMessageResponse deleteHall(UUID hallId, HttpServletRequest httpRequest);
}

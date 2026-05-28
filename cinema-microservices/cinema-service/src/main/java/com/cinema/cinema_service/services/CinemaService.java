package com.cinema.cinema_service.services;

import com.cinema.cinema_service.dto.request.AssignCinemaStaffRequest;
import com.cinema.cinema_service.dto.request.CinemaField;
import com.cinema.cinema_service.dto.request.CreateCinemaRequest;
import com.cinema.cinema_service.dto.request.UpdateCinemaRequest;
import com.cinema.cinema_service.dto.request.UpdateCinemaStatusRequest;
import com.cinema.cinema_service.dto.response.CinemaResponse;
import com.cinema.cinema_service.dto.response.CinemaStaffResponse;
import com.cinema.dto.request.PageRequest;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.dto.response.PageResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.UUID;

public interface CinemaService {

    ActionMessageResponse createCinema(CreateCinemaRequest request, HttpServletRequest httpRequest);

    CinemaResponse getCinemaById(UUID cinemaId);

    PageResponse<CinemaResponse> searchCinemas(PageRequest<CinemaField> request);

    ActionMessageResponse updateCinema(UUID cinemaId, UpdateCinemaRequest request, HttpServletRequest httpRequest);

    ActionMessageResponse updateCinemaStatus(UUID cinemaId, UpdateCinemaStatusRequest request,
                                             HttpServletRequest httpRequest);

    ActionMessageResponse deleteCinema(UUID cinemaId, HttpServletRequest httpRequest);

    ActionMessageResponse assignStaff(UUID cinemaId, AssignCinemaStaffRequest request, HttpServletRequest httpRequest);

    ActionMessageResponse updateStaffAssignment(UUID cinemaId, AssignCinemaStaffRequest request,
                                                HttpServletRequest httpRequest);

    ActionMessageResponse unassignStaff(UUID cinemaId, UUID staffId, HttpServletRequest httpRequest);

    List<CinemaStaffResponse> getCinemaStaffs(UUID cinemaId, HttpServletRequest httpRequest);

    List<CinemaResponse> getMyManagedCinemas(HttpServletRequest httpRequest);

    List<CinemaResponse> getAllActiveCinemas();

    List<CinemaResponse> getCinemasByManagerId(UUID managerId);

    List<CinemaResponse> getAccessibleCinemasByUserId(UUID userId, String role);
}

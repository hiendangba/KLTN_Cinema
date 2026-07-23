package com.cinema.cinema_service.controller;

import com.cinema.Enum.SuccessMessage;
import com.cinema.cinema_service.dto.request.CinemaField;
import com.cinema.cinema_service.dto.request.CreateCinemaRequest;
import com.cinema.cinema_service.dto.request.UpdateCinemaRequest;
import com.cinema.cinema_service.dto.request.UpdateCinemaStatusRequest;
import com.cinema.cinema_service.dto.response.CinemaResponse;
import com.cinema.cinema_service.dto.response.CinemaStaffResponse;
import com.cinema.cinema_service.services.CinemaService;
import com.cinema.controller.BaseController;
import com.cinema.dto.request.PageRequest;
import com.cinema.dto.response.APIResponse;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.dto.response.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/cinemas")
@Slf4j
@RequiredArgsConstructor
public class CinemaController extends BaseController {
    private final CinemaService cinemaService;

    @PostMapping
    public ResponseEntity<APIResponse<ActionMessageResponse>> createCinema(
            @Valid @RequestBody CreateCinemaRequest request,
            HttpServletRequest httpRequest) {
        ActionMessageResponse response = cinemaService.createCinema(request, httpRequest);
        return created(SuccessMessage.CINEMA_CREATED, response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<APIResponse<CinemaResponse>> getCinemaById(@PathVariable UUID id) {
        CinemaResponse response = cinemaService.getCinemaById(id);
        return ok(SuccessMessage.CINEMA_FETCHED, response);
    }

    @PostMapping("/search")
    public ResponseEntity<APIResponse<PageResponse<CinemaResponse>>> searchCinemas(
            @Valid @RequestBody PageRequest<CinemaField> request) {
        PageResponse<CinemaResponse> response = cinemaService.searchCinemas(request);
        return ok(SuccessMessage.CINEMAS_SEARCHED, response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<APIResponse<ActionMessageResponse>> updateCinema(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCinemaRequest request,
            HttpServletRequest httpRequest) {
        ActionMessageResponse response = cinemaService.updateCinema(id, request, httpRequest);
        return ok(SuccessMessage.CINEMA_UPDATED, response);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<APIResponse<ActionMessageResponse>> updateCinemaStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCinemaStatusRequest request,
            HttpServletRequest httpRequest) {
        ActionMessageResponse response = cinemaService.updateCinemaStatus(id, request, httpRequest);
        return ok(SuccessMessage.CINEMA_STATUS_UPDATED, response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<APIResponse<ActionMessageResponse>> deleteCinema(
            @PathVariable UUID id,
            HttpServletRequest httpRequest) {
        ActionMessageResponse response = cinemaService.deleteCinema(id, httpRequest);
        return ok(SuccessMessage.CINEMA_DELETED, response);
    }

    @GetMapping("/{id}/staffs")
    public ResponseEntity<APIResponse<List<CinemaStaffResponse>>> getCinemaStaffs(
            @PathVariable UUID id,
            HttpServletRequest httpRequest) {
        List<CinemaStaffResponse> response = cinemaService.getCinemaStaffs(id, httpRequest);
        return ok(SuccessMessage.CINEMA_STAFFS_FETCHED, response);
    }

    @PostMapping("/me/search")
    public ResponseEntity<APIResponse<PageResponse<CinemaResponse>>> searchMyManagedCinemas(
            @Valid @RequestBody PageRequest<CinemaField> request,
            HttpServletRequest httpRequest) {
        PageResponse<CinemaResponse> response = cinemaService.searchMyManagedCinemas(request, httpRequest);
        return ok(SuccessMessage.CINEMAS_SEARCHED, response);
    }
}

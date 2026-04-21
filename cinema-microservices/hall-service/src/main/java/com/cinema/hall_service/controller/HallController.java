package com.cinema.hall_service.controller;

import com.cinema.controller.BaseController;
import com.cinema.dto.request.CursorPageRequest;
import com.cinema.dto.response.APIResponse;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.dto.response.CursorPageResponse;
import com.cinema.hall_service.dto.request.HallCreateRequest;
import com.cinema.hall_service.dto.request.HallField;
import com.cinema.hall_service.dto.request.UpdateHallRequest;
import com.cinema.hall_service.dto.request.UpdateHallLayoutRequest;
import com.cinema.hall_service.dto.request.UpdateHallStatusRequest;
import com.cinema.hall_service.dto.response.HallResponse;
import com.cinema.hall_service.services.HallService;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/halls")
@Slf4j
@RequiredArgsConstructor
public class HallController extends BaseController {
    private final HallService hallService;

    @PostMapping
    public ResponseEntity<APIResponse<ActionMessageResponse>> createHall(
            @Valid @RequestBody HallCreateRequest request,
            HttpServletRequest httpRequest) {
        ActionMessageResponse response = hallService.createHall(request, httpRequest);
        return created(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<APIResponse<HallResponse>> getHallById(@PathVariable UUID id) {
        HallResponse response = hallService.getHallById(id);
        return ok(response);
    }

    @PostMapping("/search")
    public ResponseEntity<APIResponse<CursorPageResponse<HallResponse>>> searchHalls(
            @Valid @RequestBody CursorPageRequest<HallField> request) {
        CursorPageResponse<HallResponse> response = hallService.searchHalls(request);
        return ok(response);
    }

    @PatchMapping("/{id}/layout")
    public ResponseEntity<APIResponse<ActionMessageResponse>> updateHallLayout(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateHallLayoutRequest request,
            HttpServletRequest httpRequest) {
        ActionMessageResponse response = hallService.updateHallLayout(id, request, httpRequest);
        return ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<APIResponse<ActionMessageResponse>> updateHall(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateHallRequest request,
            HttpServletRequest httpRequest) {
        ActionMessageResponse response = hallService.updateHall(id, request, httpRequest);
        return ok(response);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<APIResponse<ActionMessageResponse>> updateHallStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateHallStatusRequest request,
            HttpServletRequest httpRequest) {
        ActionMessageResponse response = hallService.updateHallStatus(id, request, httpRequest);
        return ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<APIResponse<ActionMessageResponse>> deleteHall(@PathVariable UUID id,
                                                                         HttpServletRequest httpRequest) {
        ActionMessageResponse response = hallService.deleteHall(id, httpRequest);
        return ok(response);
    }
}

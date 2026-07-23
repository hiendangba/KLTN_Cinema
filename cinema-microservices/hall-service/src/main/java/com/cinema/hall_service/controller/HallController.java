package com.cinema.hall_service.controller;

import com.cinema.Enum.SuccessMessage;
import com.cinema.controller.BaseController;
import com.cinema.dto.request.PageRequest;
import com.cinema.dto.response.APIResponse;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.dto.response.PageResponse;
import com.cinema.hall_service.dto.request.CreateHallRequest;
import com.cinema.hall_service.dto.request.HallField;
import com.cinema.hall_service.dto.request.UpdateHallRequest;
import com.cinema.hall_service.dto.response.HallResponse;
import com.cinema.hall_service.services.HallService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
            @Valid @RequestBody CreateHallRequest request,
            HttpServletRequest httpRequest) {
        ActionMessageResponse response = hallService.createHall(request, httpRequest);
        return created(SuccessMessage.HALL_CREATED, response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<APIResponse<HallResponse>> getHallById(@PathVariable UUID id) {
        HallResponse response = hallService.getHallById(id);
        return ok(SuccessMessage.HALL_FETCHED, response);
    }

    @PostMapping("/search")
    public ResponseEntity<APIResponse<PageResponse<HallResponse>>> searchHalls(
            @Valid @RequestBody PageRequest<HallField> request) {
        PageResponse<HallResponse> response = hallService.searchHalls(request);
        return ok(SuccessMessage.HALLS_SEARCHED, response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<APIResponse<ActionMessageResponse>> updateHall(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateHallRequest request,
            HttpServletRequest httpRequest) {
        ActionMessageResponse response = hallService.updateHall(id, request, httpRequest);
        return ok(SuccessMessage.HALL_UPDATED, response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<APIResponse<ActionMessageResponse>> deleteHall(@PathVariable UUID id,
                                                                         HttpServletRequest httpRequest) {
        ActionMessageResponse response = hallService.deleteHall(id, httpRequest);
        return ok(SuccessMessage.HALL_DELETED, response);
    }

}

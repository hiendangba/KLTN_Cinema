package com.cinema.hall_service.controller;

import com.cinema.controller.BaseController;
import com.cinema.dto.request.PageRequest;
import com.cinema.dto.response.APIResponse;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.dto.response.PageResponse;
import com.cinema.hall_service.dto.request.AddHallImageRequest;
import com.cinema.hall_service.dto.request.HallCreateRequest;
import com.cinema.hall_service.dto.request.HallField;
import com.cinema.hall_service.dto.request.HallLayoutDefinitionRequest;
import com.cinema.hall_service.dto.request.ReplaceHallSeatsRequest;
import com.cinema.hall_service.dto.request.UpdateHallRequest;
import com.cinema.hall_service.dto.request.UpdateHallStatusRequest;
import com.cinema.hall_service.dto.response.HallImageResponse;
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

import java.util.List;
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
    public ResponseEntity<APIResponse<PageResponse<HallResponse>>> searchHalls(
            @Valid @RequestBody PageRequest<HallField> request) {
        PageResponse<HallResponse> response = hallService.searchHalls(request);
        return ok(response);
    }

    @PutMapping("/{id}/seats")
    public ResponseEntity<APIResponse<ActionMessageResponse>> replaceHallSeats(
            @PathVariable UUID id,
            @Valid @RequestBody ReplaceHallSeatsRequest request,
            HttpServletRequest httpRequest) {
        ActionMessageResponse response = hallService.replaceHallSeats(id, request, httpRequest);
        return ok(response);
    }

    @PostMapping("/{id}/layout-definition")
    public ResponseEntity<APIResponse<ActionMessageResponse>> createHallLayoutDefinition(
            @PathVariable UUID id,
            @Valid @RequestBody HallLayoutDefinitionRequest request,
            HttpServletRequest httpRequest) {
        ActionMessageResponse response = hallService.createHallLayoutDefinition(id, request, httpRequest);
        return created(response);
    }

    @PutMapping("/{id}/layout-definition")
    public ResponseEntity<APIResponse<ActionMessageResponse>> replaceHallLayoutDefinition(
            @PathVariable UUID id,
            @Valid @RequestBody HallLayoutDefinitionRequest request,
            HttpServletRequest httpRequest) {
        ActionMessageResponse response = hallService.replaceHallLayoutDefinition(id, request, httpRequest);
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

    @PostMapping("/{id}/images")
    public ResponseEntity<APIResponse<ActionMessageResponse>> addHallImage(
            @PathVariable UUID id,
            @Valid @RequestBody AddHallImageRequest request,
            HttpServletRequest httpRequest) {
        ActionMessageResponse response = hallService.addHallImage(id, request, httpRequest);
        return created(response);
    }

    @GetMapping("/{id}/images")
    public ResponseEntity<APIResponse<List<HallImageResponse>>> getHallImages(@PathVariable UUID id) {
        List<HallImageResponse> response = hallService.getHallImages(id);
        return ok(response);
    }

    @DeleteMapping("/{hallId}/images/{imageId}")
    public ResponseEntity<APIResponse<ActionMessageResponse>> deleteHallImage(
            @PathVariable UUID hallId,
            @PathVariable UUID imageId,
            HttpServletRequest httpRequest) {
        ActionMessageResponse response = hallService.deleteHallImage(hallId, imageId, httpRequest);
        return ok(response);
    }
}

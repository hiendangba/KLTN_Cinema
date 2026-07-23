package com.cinema.film_service.controller;

import com.cinema.Enum.SuccessMessage;
import com.cinema.controller.BaseController;
import com.cinema.dto.request.PageRequest;
import com.cinema.dto.response.APIResponse;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.dto.response.PageResponse;
import com.cinema.film_service.dto.request.CreateFilmTypeRequest;
import com.cinema.film_service.dto.request.FilmTypeField;
import com.cinema.film_service.dto.request.UpdateFilmTypeRequest;
import com.cinema.film_service.dto.response.FilmTypeResponse;
import com.cinema.film_service.services.TypeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/films/types")
@RequiredArgsConstructor
public class TypeController extends BaseController {

    private final TypeService typeService;

    @PostMapping
    public ResponseEntity<APIResponse<ActionMessageResponse>> createType(
            @Valid @RequestBody CreateFilmTypeRequest request,
            HttpServletRequest httpRequest) {
        return created(SuccessMessage.CREATED, typeService.createType(request, httpRequest));
    }

    @PutMapping("/{id}")
    public ResponseEntity<APIResponse<ActionMessageResponse>> updateType(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateFilmTypeRequest request,
            HttpServletRequest httpRequest) {
        return ok(SuccessMessage.UPDATED, typeService.updateType(id, request, httpRequest));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<APIResponse<ActionMessageResponse>> deleteType(
            @PathVariable UUID id,
            HttpServletRequest httpRequest) {
        return ok(SuccessMessage.DELETED, typeService.deleteType(id, httpRequest));
    }

    @GetMapping("/{id}")
    public ResponseEntity<APIResponse<FilmTypeResponse>> getTypeById(@PathVariable UUID id) {
        return ok(SuccessMessage.FETCHED, typeService.getTypeById(id));
    }

    @PostMapping("/search")
    public ResponseEntity<APIResponse<PageResponse<FilmTypeResponse>>> searchTypes(
            @Valid @RequestBody PageRequest<FilmTypeField> request) {
        return ok(SuccessMessage.LISTED, typeService.searchTypes(request));
    }
}

package com.cinema.film_service.controller;

import com.cinema.Enum.SuccessMessage;
import com.cinema.controller.BaseController;
import com.cinema.dto.response.APIResponse;
import com.cinema.film_service.dto.request.BatchFilmRequest;
import com.cinema.film_service.dto.request.CreateFilmRequest;
import com.cinema.film_service.dto.request.FilmCursorPageRequest;
import com.cinema.film_service.dto.request.FilmField;
import com.cinema.film_service.dto.request.UpdateFilmRequest;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.film_service.dto.response.BatchFilmResponse;
import com.cinema.film_service.dto.response.FilmResponse;
import com.cinema.film_service.services.FilmService;
import com.cinema.dto.response.CursorPageResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/films")
@Slf4j
@RequiredArgsConstructor
public class FilmController extends BaseController {

    private final FilmService filmService;

    @PostMapping("/search")
    public ResponseEntity<APIResponse<CursorPageResponse<FilmResponse>>> searchFilms(
            @Valid @RequestBody FilmCursorPageRequest request) {
        CursorPageResponse<FilmResponse> response = filmService.searchFilms(request);
        return ok(SuccessMessage.FILMS_SEARCHED, response);
    }

    @PostMapping("/customer/search")
    public ResponseEntity<APIResponse<CursorPageResponse<FilmResponse>>> searchCustomerFilms(
        @Valid @RequestBody FilmCursorPageRequest request,
        HttpServletRequest httpRequest) {
        CursorPageResponse<FilmResponse> response = filmService.searchCustomerFilms(request, httpRequest);
        return ok(SuccessMessage.FILMS_SEARCHED, response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<APIResponse<FilmResponse>> getFilm(@PathVariable UUID id) {
        FilmResponse response = filmService.getFilmById(id);
        return ok(SuccessMessage.FILM_FETCHED, response);
    }

    @PostMapping("/batch")
    public ResponseEntity<APIResponse<BatchFilmResponse>> getFilmsInBatch(
            @Valid @RequestBody BatchFilmRequest batchFilmRequest) {
        BatchFilmResponse response = filmService.getFilmsInBatch(batchFilmRequest);
        return ok(SuccessMessage.FILMS_BATCH_FETCHED, response);
    }

    @PostMapping
    public ResponseEntity<APIResponse<ActionMessageResponse>> createFilm(
            @Valid @RequestBody CreateFilmRequest request,
            HttpServletRequest httpRequest) {
        log.info("Request tạo phim: {}", request.getTitle());
        ActionMessageResponse response = filmService.createFilm(request, httpRequest);
        return created(SuccessMessage.FILM_CREATED, response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<APIResponse<ActionMessageResponse>> updateFilm(
            @PathVariable UUID id,
        @Valid @RequestBody UpdateFilmRequest request,
        HttpServletRequest httpRequest) {
        log.info("Request cập nhật phim với ID: {}", id);
        ActionMessageResponse response = filmService.updateFilm(id, request, httpRequest);
        return ok(SuccessMessage.FILM_UPDATED, response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<APIResponse<ActionMessageResponse>> deleteFilm(
        @PathVariable UUID id,
        HttpServletRequest httpRequest) {
        log.info("Request xóa phim với ID: {}", id);
        ActionMessageResponse response = filmService.deleteFilm(id, httpRequest);
        return ok(SuccessMessage.FILM_DELETED, response);
    }
}

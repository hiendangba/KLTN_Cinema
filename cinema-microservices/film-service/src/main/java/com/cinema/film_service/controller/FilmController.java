package com.cinema.film_service.controller;

import com.cinema.controller.BaseController;
import com.cinema.dto.response.APIResponse;
import com.cinema.film_service.dto.request.BatchFilmRequest;
import com.cinema.film_service.dto.request.CreateFilmRequest;
import com.cinema.film_service.dto.request.FilmField;
import com.cinema.film_service.dto.request.UpdateFilmRequest;
import com.cinema.film_service.dto.response.BatchFilmResponse;
import com.cinema.film_service.dto.response.FilmResponse;
import com.cinema.film_service.services.FilmService;
import com.cinema.dto.request.CursorPageRequest;
import com.cinema.dto.response.CursorPageResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/films")
@Slf4j
@RequiredArgsConstructor
public class FilmController extends BaseController {

    private final FilmService filmService;

    @PostMapping("/search")
    public ResponseEntity<APIResponse<CursorPageResponse<FilmResponse>>> searchFilms(
            @RequestBody CursorPageRequest<FilmField> request) {
        CursorPageResponse<FilmResponse> response = filmService.searchFilms(request);
        return ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<APIResponse<FilmResponse>> getFilm(@PathVariable UUID id) {
        FilmResponse response = filmService.getFilmById(id);
        return ok(response);
    }

    @PostMapping("/batch")
    public ResponseEntity<APIResponse<BatchFilmResponse>> getFilmsInBatch(
            @Valid @RequestBody BatchFilmRequest batchFilmRequest) {
        BatchFilmResponse response = filmService.getFilmsInBatch(batchFilmRequest);
        return ok(response);
    }

    @PostMapping
    public ResponseEntity<APIResponse<FilmResponse>> createFilm(@Valid @RequestBody CreateFilmRequest request,
                                                                HttpServletRequest httpRequest) {
        log.info("Request tạo phim: {}", request.getTitle());
        FilmResponse response = filmService.createFilm(request, httpRequest);
        return created(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<APIResponse<FilmResponse>> updateFilm(@PathVariable UUID id,
                                                                @Valid @RequestBody UpdateFilmRequest request, HttpServletRequest httpRequest) {
        log.info("Request cập nhật phim với ID: {}", id);
        FilmResponse response = filmService.updateFilm(id, request, httpRequest);
        return ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFilm(@PathVariable UUID id, HttpServletRequest httpRequest) {
        log.info("Request xóa phim với ID: {}", id);
        filmService.deleteFilm(id, httpRequest);
        return noContent();
    }
}

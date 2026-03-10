package com.cinema.film_service.controller;

import com.cinema.controller.BaseController;
import com.cinema.dto.response.APIResponse;
import com.cinema.film_service.dto.request.CreateFilmRequest;
import com.cinema.film_service.dto.request.FilmSortField;
import com.cinema.film_service.dto.request.UpdateFilmRequest;
import com.cinema.film_service.dto.response.FilmResponse;
import com.cinema.film_service.services.FilmService;
import com.cinema.dto.request.CursorPageRequest;
import com.cinema.dto.response.CursorPageResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/films")
@Slf4j
public class FilmController extends BaseController {

    private final FilmService filmService;

    public FilmController(FilmService filmService) {
        this.filmService = filmService;
    }

    @GetMapping
    public ResponseEntity<APIResponse<CursorPageResponse<FilmResponse>>> getAllFilms(
            @ModelAttribute CursorPageRequest<FilmSortField> request) {
        CursorPageResponse<FilmResponse> response = filmService.getAllFilms(request);
        return ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<APIResponse<FilmResponse>> getFilm(@PathVariable UUID id) {
        FilmResponse response = filmService.getFilmById(id);
        return ok(response);
    }

    @PostMapping
    public ResponseEntity<APIResponse<FilmResponse>> createFilm(@Valid @RequestBody CreateFilmRequest request, HttpServletRequest httpRequest) {
        log.info("Request tạo phim: {}", request.getTitle());
        FilmResponse response = filmService.createFilm(request, httpRequest);
        return created(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<APIResponse<FilmResponse>> updateFilm(@PathVariable UUID id, @Valid @RequestBody UpdateFilmRequest request, HttpServletRequest httpRequest) {
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

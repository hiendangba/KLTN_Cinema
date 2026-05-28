package com.cinema.film_service.services;

import com.cinema.film_service.dto.request.FilmCursorPageRequest;
import com.cinema.dto.response.CursorPageResponse;
import com.cinema.film_service.dto.request.BatchFilmRequest;
import com.cinema.film_service.dto.request.CreateFilmRequest;
import com.cinema.film_service.dto.request.FilmField;
import com.cinema.film_service.dto.request.UpdateFilmRequest;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.film_service.dto.response.BatchFilmResponse;
import com.cinema.film_service.dto.response.FilmResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

public interface FilmService {

    ActionMessageResponse createFilm(CreateFilmRequest request, HttpServletRequest httpRequest);

    ActionMessageResponse updateFilm(UUID id, UpdateFilmRequest request, HttpServletRequest httpRequest);

    FilmResponse getFilmById(UUID id);

    BatchFilmResponse getFilmsInBatch(BatchFilmRequest request);

    CursorPageResponse<FilmResponse> searchFilms(FilmCursorPageRequest request);

    ActionMessageResponse deleteFilm(UUID id, HttpServletRequest httpRequest);
}

package com.cinema.film_service.services;

// import các class cursor paging mới nếu có

import com.cinema.dto.request.CursorPageRequest;
import com.cinema.dto.response.CursorPageResponse;
import com.cinema.film_service.dto.request.CreateFilmRequest;
import com.cinema.film_service.dto.request.FilmSortField;
import com.cinema.film_service.dto.request.UpdateFilmRequest;
import com.cinema.film_service.dto.response.FilmResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

public interface FilmService {

    FilmResponse createFilm(CreateFilmRequest request, HttpServletRequest httpRequest);

    FilmResponse updateFilm(UUID id, UpdateFilmRequest request, HttpServletRequest httpRequest);

    FilmResponse getFilmById(UUID id);

    CursorPageResponse<FilmResponse> getAllFilms(CursorPageRequest<FilmSortField> request);

    void deleteFilm(UUID id, HttpServletRequest httpRequest);
}

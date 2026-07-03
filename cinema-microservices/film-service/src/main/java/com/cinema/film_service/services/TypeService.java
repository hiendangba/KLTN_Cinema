package com.cinema.film_service.services;

import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.dto.response.PageResponse;
import com.cinema.film_service.dto.request.CreateFilmTypeRequest;
import com.cinema.film_service.dto.request.FilmTypeField;
import com.cinema.film_service.dto.request.UpdateFilmTypeRequest;
import com.cinema.film_service.dto.response.FilmTypeResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

public interface TypeService {
    ActionMessageResponse createType(CreateFilmTypeRequest request, HttpServletRequest httpRequest);

    ActionMessageResponse updateType(UUID id, UpdateFilmTypeRequest request, HttpServletRequest httpRequest);

    ActionMessageResponse deleteType(UUID id, HttpServletRequest httpRequest);

    FilmTypeResponse getTypeById(UUID id);

    PageResponse<FilmTypeResponse> searchTypes(com.cinema.dto.request.PageRequest<FilmTypeField> request);
}

package com.cinema.film_service.services;

import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.film_service.dto.request.CreateFilmTypeRequest;
import com.cinema.film_service.dto.request.UpdateFilmTypeRequest;
import com.cinema.film_service.dto.response.FilmTypeResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.UUID;

public interface TypeService {
    ActionMessageResponse createType(CreateFilmTypeRequest request, HttpServletRequest httpRequest);

    ActionMessageResponse updateType(UUID id, UpdateFilmTypeRequest request, HttpServletRequest httpRequest);

    ActionMessageResponse deleteType(UUID id, HttpServletRequest httpRequest);

    FilmTypeResponse getTypeById(UUID id);

    List<FilmTypeResponse> listTypes();
}

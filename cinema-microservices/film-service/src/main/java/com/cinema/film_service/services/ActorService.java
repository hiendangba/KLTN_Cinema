package com.cinema.film_service.services;

import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.dto.response.PageResponse;
import com.cinema.dto.request.PageRequest;
import com.cinema.film_service.dto.request.ActorField;
import com.cinema.film_service.dto.request.CreateActorRequest;
import com.cinema.film_service.dto.request.UpdateActorRequest;
import com.cinema.film_service.dto.response.ActorResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

public interface ActorService {
    ActionMessageResponse createActor(CreateActorRequest request, HttpServletRequest httpRequest);

    ActionMessageResponse updateActor(UUID id, UpdateActorRequest request, HttpServletRequest httpRequest);

    ActionMessageResponse deleteActor(UUID id, HttpServletRequest httpRequest);

    ActorResponse getActorById(UUID id);

    PageResponse<ActorResponse> searchActors(PageRequest<ActorField> request);
}

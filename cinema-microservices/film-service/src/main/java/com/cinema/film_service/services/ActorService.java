package com.cinema.film_service.services;

import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.film_service.dto.request.CreateActorRequest;
import com.cinema.film_service.dto.request.UpdateActorRequest;
import com.cinema.film_service.dto.response.ActorResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.UUID;

public interface ActorService {
    ActionMessageResponse createActor(CreateActorRequest request, HttpServletRequest httpRequest);

    ActionMessageResponse updateActor(UUID id, UpdateActorRequest request, HttpServletRequest httpRequest);

    ActionMessageResponse deleteActor(UUID id, HttpServletRequest httpRequest);

    ActorResponse getActorById(UUID id);

    List<ActorResponse> listActors();
}

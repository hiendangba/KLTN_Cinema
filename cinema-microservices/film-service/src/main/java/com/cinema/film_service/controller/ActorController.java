package com.cinema.film_service.controller;

import com.cinema.Enum.SuccessMessage;
import com.cinema.controller.BaseController;
import com.cinema.dto.request.PageRequest;
import com.cinema.dto.response.APIResponse;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.dto.response.PageResponse;
import com.cinema.film_service.dto.request.ActorField;
import com.cinema.film_service.dto.request.CreateActorRequest;
import com.cinema.film_service.dto.request.UpdateActorRequest;
import com.cinema.film_service.dto.response.ActorResponse;
import com.cinema.film_service.services.ActorService;
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
@RequestMapping("/api/films/actors")
@RequiredArgsConstructor
public class ActorController extends BaseController {

    private final ActorService actorService;

    @PostMapping
    public ResponseEntity<APIResponse<ActionMessageResponse>> createActor(
            @Valid @RequestBody CreateActorRequest request,
            HttpServletRequest httpRequest) {
        return created(SuccessMessage.CREATED, actorService.createActor(request, httpRequest));
    }

    @PutMapping("/{id}")
    public ResponseEntity<APIResponse<ActionMessageResponse>> updateActor(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateActorRequest request,
            HttpServletRequest httpRequest) {
        return ok(SuccessMessage.UPDATED, actorService.updateActor(id, request, httpRequest));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<APIResponse<ActionMessageResponse>> deleteActor(
            @PathVariable UUID id,
            HttpServletRequest httpRequest) {
        return ok(SuccessMessage.DELETED, actorService.deleteActor(id, httpRequest));
    }

    @GetMapping("/{id}")
    public ResponseEntity<APIResponse<ActorResponse>> getActorById(@PathVariable UUID id) {
        return ok(SuccessMessage.FETCHED, actorService.getActorById(id));
    }

    @PostMapping("/search")
    public ResponseEntity<APIResponse<PageResponse<ActorResponse>>> searchActors(
            @Valid @RequestBody PageRequest<ActorField> request) {
        return ok(SuccessMessage.LISTED, actorService.searchActors(request));
    }
}

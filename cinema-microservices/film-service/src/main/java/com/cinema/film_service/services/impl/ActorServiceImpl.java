package com.cinema.film_service.services.impl;

import com.cinema.Enum.SuccessMessage;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.film_service.dto.request.CreateActorRequest;
import com.cinema.film_service.dto.request.UpdateActorRequest;
import com.cinema.film_service.dto.response.ActorResponse;
import com.cinema.film_service.entity.Actor;
import com.cinema.film_service.repository.ActorRepository;
import com.cinema.film_service.services.ActorService;
import com.cinema.film_service.services.FilmCatalogSyncService;
import com.cinema.http.HeaderNames;
import com.cinema.http.RequestAuthUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ActorServiceImpl implements ActorService {

    private final ActorRepository actorRepository;
    private final FilmCatalogSyncService filmCatalogSyncService;

    @Override
    @Transactional
    public ActionMessageResponse createActor(CreateActorRequest request, HttpServletRequest httpRequest) {
        validateAdmin(httpRequest, "createActor");
        String name = normalize(request.getName());
        if (actorRepository.existsByNameIgnoreCaseAndIsDeletedFalse(name)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        Actor actor = Actor.builder()
                .name(name)
                .birthYear(request.getBirthYear())
                .hometown(normalizeOptional(request.getHometown()))
                .avatarUrl(normalizeOptional(request.getAvatarUrl()))
                .build();
        actorRepository.save(actor);
        return ActionMessageResponse.builder()
                .message(SuccessMessage.CREATED.getMessage())
                .build();
    }

    @Override
    @Transactional
    public ActionMessageResponse updateActor(UUID id, UpdateActorRequest request, HttpServletRequest httpRequest) {
        validateAdmin(httpRequest, "updateActor");
        Actor actor = getActiveActorOrThrow(id);
        actor.setName(normalize(request.getName()));
        if (actorRepository.existsByNameIgnoreCaseAndIdNotAndIsDeletedFalse(actor.getName(), id)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        actor.setBirthYear(request.getBirthYear());
        actor.setHometown(normalizeOptional(request.getHometown()));
        actor.setAvatarUrl(normalizeOptional(request.getAvatarUrl()));
        actorRepository.save(actor);
        filmCatalogSyncService.refreshFilmsForActor(id);
        return ActionMessageResponse.builder()
                .message(SuccessMessage.UPDATED.getMessage())
                .build();
    }

    @Override
    @Transactional
    public ActionMessageResponse deleteActor(UUID id, HttpServletRequest httpRequest) {
        validateAdmin(httpRequest, "deleteActor");
        Actor actor = getActiveActorOrThrow(id);
        actor.setIsDeleted(true);
        actorRepository.save(actor);
        filmCatalogSyncService.refreshFilmsForActor(id);
        return ActionMessageResponse.builder()
                .message(SuccessMessage.DELETED.getMessage())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ActorResponse getActorById(UUID id) {
        return toResponse(getActiveActorOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActorResponse> listActors() {
        return actorRepository.findAllByIsDeletedFalseOrderByNameAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    private Actor getActiveActorOrThrow(UUID id) {
        return actorRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private ActorResponse toResponse(Actor actor) {
        return ActorResponse.builder()
                .id(actor.getId())
                .name(actor.getName())
                .birthYear(actor.getBirthYear())
                .hometown(actor.getHometown())
                .avatarUrl(actor.getAvatarUrl())
                .timeCreated(actor.getTimeCreated())
                .timeUpdated(actor.getTimeUpdated())
                .build();
    }

    private String normalize(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return value.trim();
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void validateAdmin(HttpServletRequest httpRequest, String action) {
        RequestAuthUtils.requireRole(httpRequest, HeaderNames.ROLE_ADMIN, log, action);
    }
}

package com.cinema.film_service.services.impl;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.film_service.entity.Actor;
import com.cinema.film_service.entity.Film;
import com.cinema.film_service.entity.FilmType;
import com.cinema.film_service.repository.ActorRepository;
import com.cinema.film_service.repository.FilmRepository;
import com.cinema.film_service.repository.FilmTypeRepository;
import com.cinema.film_service.services.FilmCatalogSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FilmCatalogSyncServiceImpl implements FilmCatalogSyncService {

    private final FilmRepository filmRepository;
    private final FilmTypeRepository filmTypeRepository;
    private final ActorRepository actorRepository;

    @Override
    @Transactional
    public Set<FilmType> resolveTypes(List<UUID> typeIds, String legacyTypeValue) {
        if (typeIds != null && !typeIds.isEmpty()) {
            List<FilmType> types = filmTypeRepository.findAllByIdInAndIsDeletedFalse(typeIds);
            if (types.size() != new LinkedHashSet<>(typeIds).size()) {
                throw new BusinessException(ErrorCode.NOT_FOUND);
            }
            return new LinkedHashSet<>(types);
        }

        Set<String> typeNames = parseNames(legacyTypeValue);
        Set<FilmType> resolved = new LinkedHashSet<>();
        for (String typeName : typeNames) {
            resolved.add(resolveOrCreateType(typeName));
        }
        return resolved;
    }

    @Override
    @Transactional
    public Set<Actor> resolveActors(List<UUID> actorIds, String legacyActorValue) {
        if (actorIds != null && !actorIds.isEmpty()) {
            List<Actor> actors = actorRepository.findAllByIdInAndIsDeletedFalse(actorIds);
            if (actors.size() != new LinkedHashSet<>(actorIds).size()) {
                throw new BusinessException(ErrorCode.NOT_FOUND);
            }
            return new LinkedHashSet<>(actors);
        }

        Set<String> actorNames = parseNames(legacyActorValue);
        Set<Actor> resolved = new LinkedHashSet<>();
        for (String actorName : actorNames) {
            resolved.add(resolveOrCreateActor(actorName));
        }
        return resolved;
    }

    @Override
    @Transactional
    public void syncFilmDisplayFields(Film film) {
        if (film == null) {
            return;
        }
        film.setTypes(filterActiveTypes(film.getTypes()));
        film.setActors(filterActiveActors(film.getActors()));
        film.setType(joinTypeNames(film.getTypes()));
        film.setActor(joinActorNames(film.getActors()));
    }

    @Override
    @Transactional
    public void refreshFilmsForType(UUID typeId) {
        List<Film> films = filmRepository.findDistinctByTypes_IdAndIsDeletedFalse(typeId);
        if (films.isEmpty()) {
            return;
        }
        films.forEach(this::syncFilmDisplayFields);
        filmRepository.saveAll(films);
    }

    @Override
    @Transactional
    public void refreshFilmsForActor(UUID actorId) {
        List<Film> films = filmRepository.findDistinctByActors_IdAndIsDeletedFalse(actorId);
        if (films.isEmpty()) {
            return;
        }
        films.forEach(this::syncFilmDisplayFields);
        filmRepository.saveAll(films);
    }

    @Override
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void backfillLegacyFilmRelations() {
        List<Film> films = filmRepository.findAll();
        boolean changed = false;
        for (Film film : films) {
            if (film.getIsDeleted() != null && film.getIsDeleted()) {
                continue;
            }
            Set<FilmType> resolvedTypes = film.getTypes() == null || film.getTypes().isEmpty()
                    ? resolveTypes(null, film.getType())
                    : filterActiveTypes(film.getTypes());
            Set<Actor> resolvedActors = film.getActors() == null || film.getActors().isEmpty()
                    ? resolveActors(null, film.getActor())
                    : filterActiveActors(film.getActors());
            Set<FilmType> currentTypes = film.getTypes() == null ? Set.of() : new LinkedHashSet<>(film.getTypes());
            Set<Actor> currentActors = film.getActors() == null ? Set.of() : new LinkedHashSet<>(film.getActors());
            if (!Objects.equals(currentTypes, resolvedTypes)
                    || !Objects.equals(currentActors, resolvedActors)) {
                film.setTypes(resolvedTypes);
                film.setActors(resolvedActors);
                syncFilmDisplayFields(film);
                changed = true;
            }
        }
        if (changed) {
            filmRepository.saveAll(films);
        }
    }

    private FilmType resolveOrCreateType(String rawName) {
        String name = normalize(rawName);
        if (!StringUtils.hasText(name)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        return filmTypeRepository.findByNameIgnoreCaseAndIsDeletedFalse(name)
                .orElseGet(() -> filmTypeRepository.save(FilmType.builder()
                        .name(name)
                        .build()));
    }

    private Actor resolveOrCreateActor(String rawName) {
        String name = normalize(rawName);
        if (!StringUtils.hasText(name)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        List<Actor> existingActors = actorRepository.findAllByNameIgnoreCaseAndIsDeletedFalse(name);
        if (!existingActors.isEmpty()) {
            return existingActors.get(0);
        }

        return actorRepository.save(Actor.builder()
                .name(name)
                .build());
    }

    private Set<String> parseNames(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return Set.of();
        }

        Set<String> names = new LinkedHashSet<>();
        for (String token : rawValue.split("[,;]")) {
            String name = normalize(token);
            if (StringUtils.hasText(name)) {
                names.add(name);
            }
        }
        return names;
    }

    private String joinTypeNames(Set<FilmType> types) {
        return types == null ? null : types.stream()
                .filter(type -> type != null && !Boolean.TRUE.equals(type.getIsDeleted()) && StringUtils.hasText(type.getName()))
                .map(FilmType::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.joining(", "));
    }

    private String joinActorNames(Set<Actor> actors) {
        return actors == null ? null : actors.stream()
                .filter(actor -> actor != null && !Boolean.TRUE.equals(actor.getIsDeleted()) && StringUtils.hasText(actor.getName()))
                .map(Actor::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.joining(", "));
    }

    private Set<FilmType> filterActiveTypes(Set<FilmType> types) {
        if (types == null || types.isEmpty()) {
            return new LinkedHashSet<>();
        }
        return types.stream()
                .filter(type -> type != null && !Boolean.TRUE.equals(type.getIsDeleted()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<Actor> filterActiveActors(Set<Actor> actors) {
        if (actors == null || actors.isEmpty()) {
            return new LinkedHashSet<>();
        }
        return actors.stream()
                .filter(actor -> actor != null && !Boolean.TRUE.equals(actor.getIsDeleted()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}

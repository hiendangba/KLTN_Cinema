package com.cinema.film_service.services;

import com.cinema.film_service.entity.Actor;
import com.cinema.film_service.entity.Film;
import com.cinema.film_service.entity.FilmType;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface FilmCatalogSyncService {
    Set<FilmType> resolveTypes(List<UUID> typeIds, String legacyTypeValue);

    Set<Actor> resolveActors(List<UUID> actorIds, String legacyActorValue);

    void syncFilmDisplayFields(Film film);

    void refreshFilmsForType(UUID typeId);

    void refreshFilmsForActor(UUID actorId);

    void backfillLegacyFilmRelations();
}

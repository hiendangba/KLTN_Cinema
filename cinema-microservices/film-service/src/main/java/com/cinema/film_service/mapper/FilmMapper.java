package com.cinema.film_service.mapper;

import com.cinema.film_service.dto.request.CreateFilmRequest;
import com.cinema.film_service.dto.request.UpdateFilmRequest;
import com.cinema.film_service.dto.response.ActorBriefResponse;
import com.cinema.film_service.dto.response.FilmResponse;
import com.cinema.film_service.dto.response.FilmTypeBriefResponse;
import com.cinema.film_service.entity.Film;
import com.cinema.film_service.entity.Actor;
import com.cinema.film_service.entity.FilmType;
import com.cinema.Enum.FilmEnum;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface FilmMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "timeCreated", ignore = true)
    @Mapping(target = "timeUpdated", ignore = true)
    @Mapping(target = "types", ignore = true)
    @Mapping(target = "actors", ignore = true)
    Film toEntity(CreateFilmRequest request);

    default FilmResponse toResponse(Film film) {
        if (film == null) {
            return null;
        }

        return FilmResponse.builder()
                .id(film.getId())
                .title(film.getTitle())
                .director(film.getDirector())
                .types(mapTypes(film.getTypes()))
                .actors(mapActors(film.getActors()))
                .releaseDate(film.getReleaseDate())
                .description(film.getDescription())
                .duration(film.getDuration())
                .poster(film.getPoster())
                .trailer(film.getTrailer())
                .country(film.getCountry())
                .language(film.getLanguage())
                .ageRating(film.getAgeRating())
                .status(film.getStatus())
                .timeCreated(film.getTimeCreated())
                .timeUpdated(film.getTimeUpdated())
                .build();
    }

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "timeCreated", ignore = true)
    @Mapping(target = "timeUpdated", ignore = true)
    @Mapping(target = "types", ignore = true)
    @Mapping(target = "actors", ignore = true)
    void updateEntityFromRequest(@MappingTarget Film film, UpdateFilmRequest request);

    default List<FilmTypeBriefResponse> mapTypes(Set<FilmType> types) {
        if (types == null || types.isEmpty()) {
            return List.of();
        }
        return types.stream()
                .filter(type -> type != null && !Boolean.TRUE.equals(type.getIsDeleted()))
                .sorted(Comparator.comparing(type -> Objects.toString(type.getName(), ""), String.CASE_INSENSITIVE_ORDER))
                .map(type -> FilmTypeBriefResponse.builder()
                        .name(type.getName())
                        .build())
                .toList();
    }

    default List<ActorBriefResponse> mapActors(Set<Actor> actors) {
        if (actors == null || actors.isEmpty()) {
            return List.of();
        }
        return actors.stream()
                .filter(actor -> actor != null && !Boolean.TRUE.equals(actor.getIsDeleted()))
                .sorted(Comparator.comparing(actor -> Objects.toString(actor.getName(), ""), String.CASE_INSENSITIVE_ORDER))
                .map(actor -> ActorBriefResponse.builder()
                        .name(actor.getName())
                        .birthYear(actor.getBirthYear())
                        .hometown(actor.getHometown())
                        .avatarUrl(actor.getAvatarUrl())
                        .build())
                .toList();
    }
}

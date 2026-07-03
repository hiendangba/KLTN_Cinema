package com.cinema.showtime_service.mapper;

import com.cinema.Enum.FilmEnum;
import com.cinema.grpc.film.ActorPayload;
import com.cinema.grpc.film.FilmPayload;
import com.cinema.grpc.film.FilmTypePayload;
import com.cinema.showtime_service.dto.response.ActorResponse;
import com.cinema.showtime_service.dto.response.FilmResponse;
import com.cinema.showtime_service.dto.response.FilmTypeResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.Comparator;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface FilmMapper {

    @Mapping(target = "id", expression = "java(parseUuid(payload.getId()))")
    @Mapping(target = "director", expression = "java(emptyToNull(payload.getDirector()))")
    @Mapping(target = "actor", expression = "java(emptyToNull(payload.getActor()))")
    @Mapping(target = "type", expression = "java(emptyToNull(payload.getType()))")
    @Mapping(target = "types", expression = "java(mapTypes(payload.getTypesList()))")
    @Mapping(target = "actors", expression = "java(mapActors(payload.getActorsList()))")
    @Mapping(target = "releaseDate", expression = "java(parseLocalDate(payload.getReleaseDate()))")
    @Mapping(target = "description", expression = "java(emptyToNull(payload.getDescription()))")
    @Mapping(target = "poster", expression = "java(emptyToNull(payload.getPoster()))")
    @Mapping(target = "trailer", expression = "java(emptyToNull(payload.getTrailer()))")
    @Mapping(target = "country", expression = "java(emptyToNull(payload.getCountry()))")
    @Mapping(target = "language", expression = "java(emptyToNull(payload.getLanguage()))")
    @Mapping(target = "ageRating", expression = "java(parseAgeRating(payload.getAgeRating()))")
    @Mapping(target = "status", expression = "java(parseFilmStatus(payload.getStatus()))")
    @Mapping(target = "timeCreated", expression = "java(parseLocalDateTime(payload.getTimeCreated()))")
    @Mapping(target = "timeUpdated", expression = "java(parseLocalDateTime(payload.getTimeUpdated()))")
    FilmResponse toResponse(FilmPayload payload);

    default String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    default UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return UUID.fromString(value);
    }

    default LocalDate parseLocalDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDate.parse(value);
    }

    default LocalDateTime parseLocalDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(value);
    }

    default FilmEnum.AgeRating parseAgeRating(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return FilmEnum.AgeRating.valueOf(value);
    }

    default FilmEnum.FilmStatus parseFilmStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return FilmEnum.FilmStatus.valueOf(value);
    }

    default List<FilmTypeResponse> mapTypes(List<FilmTypePayload> payloads) {
        if (payloads == null || payloads.isEmpty()) {
            return List.of();
        }
        return payloads.stream()
                .filter(payload -> payload != null && payload.getId() != null && !payload.getId().isBlank())
                .sorted(Comparator.comparing(
                        (FilmTypePayload payload) -> emptyToNull(payload.getName()),
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(payload -> FilmTypeResponse.builder()
                        .id(parseUuid(payload.getId()))
                        .name(emptyToNull(payload.getName()))
                        .timeCreated(parseLocalDateTime(payload.getTimeCreated()))
                        .timeUpdated(parseLocalDateTime(payload.getTimeUpdated()))
                        .build())
                .toList();
    }

    default List<ActorResponse> mapActors(List<ActorPayload> payloads) {
        if (payloads == null || payloads.isEmpty()) {
            return List.of();
        }
        return payloads.stream()
                .filter(payload -> payload != null && payload.getId() != null && !payload.getId().isBlank())
                .sorted(Comparator.comparing(
                        (ActorPayload payload) -> emptyToNull(payload.getName()),
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(payload -> ActorResponse.builder()
                        .id(parseUuid(payload.getId()))
                        .name(emptyToNull(payload.getName()))
                        .birthYear(payload.getBirthYear() == 0 ? null : payload.getBirthYear())
                        .hometown(emptyToNull(payload.getHometown()))
                        .avatarUrl(emptyToNull(payload.getAvatarUrl()))
                        .timeCreated(parseLocalDateTime(payload.getTimeCreated()))
                        .timeUpdated(parseLocalDateTime(payload.getTimeUpdated()))
                        .build())
                .toList();
    }
}

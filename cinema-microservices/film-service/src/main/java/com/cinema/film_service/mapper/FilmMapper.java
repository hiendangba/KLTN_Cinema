package com.cinema.film_service.mapper;

import com.cinema.film_service.dto.request.CreateFilmRequest;
import com.cinema.film_service.dto.request.UpdateFilmRequest;
import com.cinema.film_service.dto.response.FilmResponse;
import com.cinema.film_service.entity.Film;
import com.cinema.Enum.FilmEnum;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface FilmMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "timeCreated", ignore = true)
    @Mapping(target = "timeUpdated", ignore = true)
    Film toEntity(CreateFilmRequest request);

    FilmResponse toResponse(Film film);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "timeCreated", ignore = true)
    @Mapping(target = "timeUpdated", ignore = true)
    void updateEntityFromRequest(@MappingTarget Film film, UpdateFilmRequest request);
}

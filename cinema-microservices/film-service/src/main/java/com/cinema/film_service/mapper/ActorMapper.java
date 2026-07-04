package com.cinema.film_service.mapper;

import com.cinema.film_service.dto.request.CreateActorRequest;
import com.cinema.film_service.dto.request.UpdateActorRequest;
import com.cinema.film_service.dto.response.ActorResponse;
import com.cinema.film_service.entity.Actor;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ActorMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "timeCreated", ignore = true)
    @Mapping(target = "timeUpdated", ignore = true)
    @Mapping(target = "films", ignore = true)
    Actor toEntity(CreateActorRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "timeCreated", ignore = true)
    @Mapping(target = "timeUpdated", ignore = true)
    @Mapping(target = "films", ignore = true)
    void updateEntityFromRequest(@MappingTarget Actor actor, UpdateActorRequest request);

    ActorResponse toResponse(Actor actor);
}

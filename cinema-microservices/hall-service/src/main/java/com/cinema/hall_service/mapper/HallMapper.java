package com.cinema.hall_service.mapper;

import com.cinema.hall_service.dto.request.CreateHallRequest;
import com.cinema.hall_service.dto.request.UpdateHallRequest;
import com.cinema.hall_service.dto.response.HallResponse;
import com.cinema.hall_service.entity.Hall;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface HallMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "cinemaId", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "timeCreated", ignore = true)
    @Mapping(target = "timeUpdated", ignore = true)
    Hall toEntity(CreateHallRequest request);

    @Mapping(target = "seats", ignore = true)
    @Mapping(target = "images", ignore = true)
    @Mapping(target = "cinemaResponse", ignore = true)
    HallResponse toResponse(Hall hall);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "cinemaId", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "timeCreated", ignore = true)
    @Mapping(target = "timeUpdated", ignore = true)
    void updateEntityFromRequest(@MappingTarget Hall hall, UpdateHallRequest request);
}

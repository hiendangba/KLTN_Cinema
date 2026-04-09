package com.cinema.hall_services.mapper;

import com.cinema.hall_services.dto.request.HallCreateRequest;
import com.cinema.hall_services.dto.response.HallResponse;
import com.cinema.hall_services.entity.Hall;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface HallMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "layoutJson", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "timeCreated", ignore = true)
    @Mapping(target = "timeUpdated", ignore = true)
    Hall toEntity(HallCreateRequest request);

    @Mapping(target = "layoutJson", ignore = true)
    HallResponse toResponse(Hall hall);
}

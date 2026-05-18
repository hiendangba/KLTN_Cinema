package com.cinema.hall_service.mapper;

import com.cinema.hall_service.dto.request.SeatUpsertRequest;
import com.cinema.hall_service.dto.response.SeatResponse;
import com.cinema.hall_service.entity.Seat;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface SeatMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "hall", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "timeCreated", ignore = true)
    @Mapping(target = "timeUpdated", ignore = true)
    Seat toEntity(SeatUpsertRequest request);

    SeatResponse toResponse(Seat seat);
}

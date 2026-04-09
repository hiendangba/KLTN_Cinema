package com.cinema.showtime_service.mapper;

import com.cinema.showtime_service.dto.request.ShowTimeCreateRequest;
import com.cinema.showtime_service.dto.response.ShowTimeResponse;
import com.cinema.showtime_service.entity.ShowTime;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ShowTimeMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "timeCreated", ignore = true)
    @Mapping(target = "timeUpdated", ignore = true)
    ShowTime toEntity(ShowTimeCreateRequest showTimeCreateRequest);

    @Mapping(target = "pricingPolicy", ignore = true)
    @Mapping(target = "film", ignore = true)
    @Mapping(target = "hall", ignore = true)
    ShowTimeResponse toResponse(ShowTime showTime);
}

package com.cinema.hall_service.mapper;

import com.cinema.hall_service.dto.response.HallImageResponse;
import com.cinema.hall_service.entity.HallImage;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface HallImageMapper {
    HallImageResponse toResponse(HallImage hallImage);
}

package com.cinema.showtime_service.mapper;

import com.cinema.showtime_service.dto.request.PricingPolicyCreateRequest;
import com.cinema.showtime_service.dto.response.PricingPolicyResponse;
import com.cinema.showtime_service.entity.PricingPolicy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PricingPolicyMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "timeCreated", ignore = true)
    @Mapping(target = "timeUpdated", ignore = true)
    PricingPolicy toEntity(PricingPolicyCreateRequest request);

    PricingPolicyResponse toResponse(PricingPolicy pricingPolicy);
}

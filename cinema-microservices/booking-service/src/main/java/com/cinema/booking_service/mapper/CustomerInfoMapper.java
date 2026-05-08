package com.cinema.booking_service.mapper;

import com.cinema.booking_service.dto.request.CreateBookingRequest;
import com.cinema.booking_service.dto.response.CustomerInfoResponse;
import com.cinema.booking_service.entity.CustomerInfo;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CustomerInfoMapper {
    CustomerInfo toEntity(CreateBookingRequest.CustomerInfo request);

    CustomerInfoResponse toResponse(CustomerInfo entity);
}

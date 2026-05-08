package com.cinema.booking_service.mapper;

import com.cinema.booking_service.dto.request.CreateBookingRequest;
import com.cinema.booking_service.dto.response.BookingSeatItemResponse;
import com.cinema.booking_service.entity.BookingSeatItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface BookingSeatItemMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "booking", ignore = true)
    @Mapping(target = "timeCreated", ignore = true)
    BookingSeatItem toEntity(CreateBookingRequest.SeatItem request);

    BookingSeatItemResponse toResponse(BookingSeatItem entity);
}

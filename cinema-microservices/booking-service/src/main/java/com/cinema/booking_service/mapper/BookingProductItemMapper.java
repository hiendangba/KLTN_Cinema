package com.cinema.booking_service.mapper;

import com.cinema.booking_service.dto.response.BookingProductItemResponse;
import com.cinema.booking_service.entity.BookingProductItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface BookingProductItemMapper {
    @Mapping(target = "productId", source = "product.id")
    BookingProductItemResponse toResponse(BookingProductItem entity);
}


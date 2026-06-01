package com.cinema.booking_service.mapper;

import com.cinema.booking_service.dto.request.CreateBookingRequest;
import com.cinema.booking_service.dto.response.BookingResponse;
import com.cinema.booking_service.entity.Booking;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR, uses = { CustomerInfoMapper.class,
        BookingSeatItemMapper.class, BookingProductItemMapper.class })
public interface BookingMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "filmId", ignore = true)
    @Mapping(target = "filmTitle", ignore = true)
    @Mapping(target = "showtimeStartDateTime", ignore = true)
    @Mapping(target = "showtimeEndDateTime", ignore = true)
    @Mapping(target = "paymentStatus", ignore = true)
    @Mapping(target = "bookingStatus", ignore = true)
    @Mapping(target = "ticketSubtotal", ignore = true)
    @Mapping(target = "productSubtotal", ignore = true)
    @Mapping(target = "finalAmount", ignore = true)
    @Mapping(target = "promotionId", ignore = true)
    @Mapping(target = "promotionCode", ignore = true)
    @Mapping(target = "promotionName", ignore = true)
    @Mapping(target = "promotionDiscountAmount", ignore = true)
    @Mapping(target = "payableAmount", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "reservedUntil", ignore = true)
    @Mapping(target = "timeCreated", ignore = true)
    @Mapping(target = "timeUpdated", ignore = true)
    @Mapping(target = "seatItems", ignore = true)
    @Mapping(target = "productItems", ignore = true)
    Booking toEntity(CreateBookingRequest request);

    @Mapping(target = "cinemaName", ignore = true)
    BookingResponse toResponse(Booking booking);
}

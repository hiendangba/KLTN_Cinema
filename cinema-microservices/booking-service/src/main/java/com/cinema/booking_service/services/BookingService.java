package com.cinema.booking_service.services;

import com.cinema.booking_service.dto.request.CreateBookingRequest;
import com.cinema.booking_service.dto.request.BookingField;
import com.cinema.booking_service.dto.request.UpdateBookingStatusRequest;
import com.cinema.booking_service.dto.response.CheckoutContextResponse;
import com.cinema.booking_service.dto.response.BookingResponse;
import com.cinema.dto.request.PageRequest;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.dto.response.PageResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.UUID;

public interface BookingService {
    BookingResponse createBooking(CreateBookingRequest request, HttpServletRequest httpRequest);

    BookingResponse getBookingById(UUID id, HttpServletRequest httpRequest);

    List<BookingResponse> getMyBookings(HttpServletRequest httpRequest);

    PageResponse<BookingResponse> searchMyBookings(PageRequest<BookingField> request, HttpServletRequest httpRequest);

    BookingResponse getMyActiveBooking(UUID showtimeId, UUID cinemaId, HttpServletRequest httpRequest);

    CheckoutContextResponse getCheckoutContext(UUID id, HttpServletRequest httpRequest);

    List<BookingResponse> getBookingsByOperatorCinema(HttpServletRequest httpRequest);

    ActionMessageResponse updateBookingStatus(UUID id, UpdateBookingStatusRequest request, HttpServletRequest httpRequest);

    ActionMessageResponse cancelBooking(UUID id, HttpServletRequest httpRequest);
}

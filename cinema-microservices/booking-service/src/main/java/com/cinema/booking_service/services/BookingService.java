package com.cinema.booking_service.services;

import com.cinema.booking_service.dto.request.CreateBookingRequest;
import com.cinema.booking_service.dto.request.BookingField;
import com.cinema.booking_service.dto.request.BookingRevenueReportRequest;
import com.cinema.booking_service.dto.request.ShowtimePerformanceReportRequest;
import com.cinema.booking_service.dto.request.UpdateBookingStatusRequest;
import com.cinema.booking_service.dto.response.CheckoutContextResponse;
import com.cinema.booking_service.dto.response.BookingResponse;
import com.cinema.booking_service.dto.response.BookingRevenueReportResponse;
import com.cinema.booking_service.dto.response.ShowtimePerformanceReportResponse;
import com.cinema.dto.request.PageRequest;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.dto.response.PageResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

public interface BookingService {
    BookingResponse createBooking(CreateBookingRequest request, HttpServletRequest httpRequest);

    BookingResponse getBookingById(UUID id, HttpServletRequest httpRequest);

    PageResponse<BookingResponse> searchMyBookings(PageRequest<BookingField> request, HttpServletRequest httpRequest);

    PageResponse<BookingResponse> searchMyActiveBookings(PageRequest<BookingField> request, HttpServletRequest httpRequest);

    BookingResponse getMyActiveBooking(UUID showtimeId, UUID cinemaId, HttpServletRequest httpRequest);

    CheckoutContextResponse getCheckoutContext(UUID id, HttpServletRequest httpRequest);

    PageResponse<BookingResponse> searchBookingsByOperatorCinema(PageRequest<BookingField> request, HttpServletRequest httpRequest);

    PageResponse<BookingResponse> searchPurchasedBookingsByOperatorCinema(
            PageRequest<BookingField> request,
            HttpServletRequest httpRequest);

    PageResponse<BookingResponse> searchUnpaidBookingsByOperatorCinema(
            PageRequest<BookingField> request,
            HttpServletRequest httpRequest);

    BookingRevenueReportResponse getAllCinemaRevenueReport(BookingRevenueReportRequest request);

    BookingRevenueReportResponse getMyCinemaRevenueReport(BookingRevenueReportRequest request, HttpServletRequest httpRequest);

    byte[] exportCinemaRevenueReport(BookingRevenueReportRequest request, HttpServletRequest httpRequest);

    ShowtimePerformanceReportResponse getAllShowtimePerformanceReport(ShowtimePerformanceReportRequest request);

    ShowtimePerformanceReportResponse getMyShowtimePerformanceReport(
            ShowtimePerformanceReportRequest request,
            HttpServletRequest httpRequest);

    ActionMessageResponse updateBookingStatus(UUID id, UpdateBookingStatusRequest request, HttpServletRequest httpRequest);

    ActionMessageResponse cancelBooking(UUID id, HttpServletRequest httpRequest);
}

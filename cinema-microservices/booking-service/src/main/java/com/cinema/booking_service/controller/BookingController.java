package com.cinema.booking_service.controller;

import com.cinema.booking_service.dto.request.CreateBookingRequest;
import com.cinema.booking_service.dto.request.BookingField;
import com.cinema.booking_service.dto.request.BookingRevenueReportRequest;
import com.cinema.booking_service.dto.request.ShowtimePerformanceReportRequest;
import com.cinema.booking_service.dto.response.BookingResponse;
import com.cinema.booking_service.dto.response.BookingRevenueReportResponse;
import com.cinema.booking_service.dto.response.CheckoutContextResponse;
import com.cinema.booking_service.dto.response.ShowtimePerformanceReportResponse;
import com.cinema.booking_service.services.BookingService;
import com.cinema.controller.BaseController;
import com.cinema.dto.request.PageRequest;
import com.cinema.dto.response.APIResponse;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.dto.response.PageResponse;
import com.cinema.excel.ExcelExportUtils;
import com.cinema.http.HeaderNames;
import com.cinema.http.RequestAuthUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController extends BaseController {

    private final BookingService bookingService;

    @PostMapping
    public ResponseEntity<APIResponse<BookingResponse>> createBooking(
            @Valid @RequestBody CreateBookingRequest request,
            HttpServletRequest httpRequest) {
        BookingResponse response = bookingService.createBooking(request, httpRequest);
        return created(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<APIResponse<BookingResponse>> getBookingById(
            @PathVariable UUID id,
            HttpServletRequest httpRequest) {
        BookingResponse response = bookingService.getBookingById(id, httpRequest);
        return ok(response);
    }

    @PostMapping("/me/active/search")
    public ResponseEntity<APIResponse<PageResponse<BookingResponse>>> searchMyActiveBookings(
            @Valid @RequestBody PageRequest<BookingField> request,
            HttpServletRequest httpRequest) {
        PageResponse<BookingResponse> response = bookingService.searchMyActiveBookings(request, httpRequest);
        return ok(response);
    }

    @PostMapping("/me/history/search")
    public ResponseEntity<APIResponse<PageResponse<BookingResponse>>> searchMyBookingHistory(
            @Valid @RequestBody PageRequest<BookingField> request,
            HttpServletRequest httpRequest) {
        PageResponse<BookingResponse> response = bookingService.searchMyBookingHistory(request, httpRequest);
        return ok(response);
    }

    @PostMapping("/cinemas/me/search")
    public ResponseEntity<APIResponse<PageResponse<BookingResponse>>> searchBookingsByOperatorCinema(
            @Valid @RequestBody PageRequest<BookingField> request,
            HttpServletRequest httpRequest) {
        PageResponse<BookingResponse> response = bookingService.searchBookingsByOperatorCinema(request, httpRequest);
        return ok(response);
    }

    @PostMapping("/cinemas/me/purchased/search")
    public ResponseEntity<APIResponse<PageResponse<BookingResponse>>> searchPurchasedBookingsByOperatorCinema(
            @Valid @RequestBody PageRequest<BookingField> request,
            HttpServletRequest httpRequest) {
        PageResponse<BookingResponse> response =
                bookingService.searchPurchasedBookingsByOperatorCinema(request, httpRequest);
        return ok(response);
    }

    @PostMapping("/cinemas/me/unpaid/search")
    public ResponseEntity<APIResponse<PageResponse<BookingResponse>>> searchUnpaidBookingsByOperatorCinema(
            @Valid @RequestBody PageRequest<BookingField> request,
            HttpServletRequest httpRequest) {
        PageResponse<BookingResponse> response =
                bookingService.searchUnpaidBookingsByOperatorCinema(request, httpRequest);
        return ok(response);
    }

    @PostMapping("/revenues/cinemas/search")
    public ResponseEntity<APIResponse<BookingRevenueReportResponse>> getAllCinemaRevenueReport(
            HttpServletRequest httpRequest,
            @Valid @RequestBody BookingRevenueReportRequest request) {
        RequestAuthUtils.requireRole(httpRequest, HeaderNames.ROLE_ADMIN);
        BookingRevenueReportResponse response = bookingService.getAllCinemaRevenueReport(request);
        return ok(response);
    }

    @PostMapping("/revenues/cinemas/me/search")
    public ResponseEntity<APIResponse<BookingRevenueReportResponse>> getMyCinemaRevenueReport(
            HttpServletRequest httpRequest,
            @Valid @RequestBody BookingRevenueReportRequest request) {
        RequestAuthUtils.requireRole(httpRequest, HeaderNames.ROLE_MANAGER);
        BookingRevenueReportResponse response = bookingService.getMyCinemaRevenueReport(request, httpRequest);
        return ok(response);
    }

    @PostMapping("/revenues/cinemas/export")
    public ResponseEntity<byte[]> exportCinemaRevenueReport(
            HttpServletRequest httpRequest,
            @Valid @RequestBody BookingRevenueReportRequest request) {
        RequestAuthUtils.requireAnyRole(httpRequest, HeaderNames.ROLE_ADMIN, HeaderNames.ROLE_MANAGER);
        byte[] file = bookingService.exportCinemaRevenueReport(request, httpRequest);
        return ExcelExportUtils.buildDownloadResponse(file, "booking_revenue_report.xlsx");
    }

    @PostMapping("/reports/showtimes/search")
    public ResponseEntity<APIResponse<ShowtimePerformanceReportResponse>> getAllShowtimePerformanceReport(
            HttpServletRequest httpRequest,
            @Valid @RequestBody ShowtimePerformanceReportRequest request) {
        RequestAuthUtils.requireRole(httpRequest, HeaderNames.ROLE_ADMIN);
        ShowtimePerformanceReportResponse response = bookingService.getAllShowtimePerformanceReport(request);
        return ok(response);
    }

    @PostMapping("/reports/showtimes/me/search")
    public ResponseEntity<APIResponse<ShowtimePerformanceReportResponse>> getMyShowtimePerformanceReport(
            HttpServletRequest httpRequest,
            @Valid @RequestBody ShowtimePerformanceReportRequest request) {
        RequestAuthUtils.requireRole(httpRequest, HeaderNames.ROLE_MANAGER);
        ShowtimePerformanceReportResponse response = bookingService.getMyShowtimePerformanceReport(request, httpRequest);
        return ok(response);
    }

    @GetMapping("/{id}/checkout-context")
    public ResponseEntity<APIResponse<CheckoutContextResponse>> getCheckoutContext(
            @PathVariable UUID id,
            HttpServletRequest httpRequest) {
        CheckoutContextResponse response = bookingService.getCheckoutContext(id, httpRequest);
        return ok(response);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<APIResponse<ActionMessageResponse>> cancelBooking(
            @PathVariable UUID id,
            HttpServletRequest httpRequest) {
        ActionMessageResponse response = bookingService.cancelBooking(id, httpRequest);
        return ok(response);
    }
}

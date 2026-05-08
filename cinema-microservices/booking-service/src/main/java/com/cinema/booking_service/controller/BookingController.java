package com.cinema.booking_service.controller;

import com.cinema.booking_service.dto.request.CreateBookingRequest;
import com.cinema.booking_service.dto.request.UpdateBookingStatusRequest;
import com.cinema.booking_service.dto.response.BookingResponse;
import com.cinema.booking_service.services.BookingService;
import com.cinema.controller.BaseController;
import com.cinema.dto.response.APIResponse;
import com.cinema.dto.response.ActionMessageResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
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

    @GetMapping("/me")
    public ResponseEntity<APIResponse<List<BookingResponse>>> getMyBookings(HttpServletRequest httpRequest) {
        List<BookingResponse> response = bookingService.getMyBookings(httpRequest);
        return ok(response);
    }

    @GetMapping("/cinemas/me")
    public ResponseEntity<APIResponse<List<BookingResponse>>> getBookingsByOperatorCinema(HttpServletRequest httpRequest) {
        List<BookingResponse> response = bookingService.getBookingsByOperatorCinema(httpRequest);
        return ok(response);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<APIResponse<ActionMessageResponse>> updateBookingStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateBookingStatusRequest request,
            HttpServletRequest httpRequest) {
        ActionMessageResponse response = bookingService.updateBookingStatus(id, request, httpRequest);
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


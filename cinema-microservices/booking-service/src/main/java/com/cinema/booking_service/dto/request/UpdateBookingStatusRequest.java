package com.cinema.booking_service.dto.request;

import com.cinema.booking_service.enums.BookingStatus;
import com.cinema.booking_service.enums.PaymentStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateBookingStatusRequest {
    @NotNull(message = "bookingStatus is required")
    private BookingStatus bookingStatus;

    private PaymentStatus paymentStatus;
}


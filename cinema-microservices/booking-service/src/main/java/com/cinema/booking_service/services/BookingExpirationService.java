package com.cinema.booking_service.services;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface BookingExpirationService {

    List<ExpiredBookingRelease> expireDueBookings(LocalDateTime now);

    record ExpiredBookingRelease(UUID showtimeId, List<String> seatCodes) {
    }
}

package com.cinema.booking_service.scheduler;

import com.cinema.booking_service.services.BookingExpirationService;
import com.cinema.booking_service.services.SeatLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingExpirationScheduler {

    private final BookingExpirationService bookingExpirationService;
    private final SeatLockService seatLockService;

    @Scheduled(fixedDelayString = "${booking.expiration-scheduler-delay-ms:60000}")
    public void expireBookings() {
        try {
            bookingExpirationService.expireDueBookings(LocalDateTime.now())
                    .forEach(release -> seatLockService.releaseSeats(release.showtimeId(), release.seatCodes()));
        } catch (Exception ex) {
            log.error("Unexpected error while expiring overdue bookings", ex);
        }
    }
}

package com.cinema.booking_service.scheduler;

import com.cinema.booking_service.services.BookingExpirationService;
import com.cinema.booking_service.services.SeatLockService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingExpirationSchedulerTest {

    @Mock
    private BookingExpirationService bookingExpirationService;

    @Mock
    private SeatLockService seatLockService;

    @InjectMocks
    private BookingExpirationScheduler bookingExpirationScheduler;

    @Test
    void expireBookings_shouldReleaseSeatLocksAfterExpiration() {
        UUID showtimeId = UUID.randomUUID();
        List<String> seatCodes = List.of("A1", "B2");
        when(bookingExpirationService.expireDueBookings(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(new BookingExpirationService.ExpiredBookingRelease(showtimeId, seatCodes)));

        bookingExpirationScheduler.expireBookings();

        verify(seatLockService).releaseSeats(showtimeId, seatCodes);
    }
}

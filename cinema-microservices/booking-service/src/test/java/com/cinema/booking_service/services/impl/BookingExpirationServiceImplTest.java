package com.cinema.booking_service.services.impl;

import com.cinema.booking_service.entity.Booking;
import com.cinema.booking_service.entity.BookingSeatItem;
import com.cinema.booking_service.enums.BookingStatus;
import com.cinema.booking_service.repository.BookingRepository;
import com.cinema.booking_service.services.BookingExpirationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingExpirationServiceImplTest {

    @Mock
    private BookingRepository bookingRepository;

    @InjectMocks
    private BookingExpirationServiceImpl bookingExpirationService;

    @Test
    void expireDueBookings_shouldMarkOverdueBookingExpiredAndReturnReleaseInfo() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 24, 8, 0);
        UUID showtimeId = UUID.randomUUID();

        Booking booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setShowtimeId(showtimeId);
        booking.setBookingStatus(BookingStatus.RESERVED);
        booking.setIsDeleted(false);
        booking.setReservedUntil(now.minusMinutes(1));

        BookingSeatItem firstSeat = new BookingSeatItem();
        firstSeat.setSeatCode("a1");
        BookingSeatItem secondSeat = new BookingSeatItem();
        secondSeat.setSeatCode("b2");
        booking.setSeatItems(List.of(firstSeat, secondSeat));

        when(bookingRepository.findDueBookingsForExpiration(eq(now), anyCollection()))
                .thenReturn(List.of(booking));

        List<BookingExpirationService.ExpiredBookingRelease> releases = bookingExpirationService.expireDueBookings(now);

        assertEquals(1, releases.size());
        assertEquals(showtimeId, releases.get(0).showtimeId());
        assertEquals(List.of("A1", "B2"), releases.get(0).seatCodes());
        assertEquals(BookingStatus.EXPIRED, booking.getBookingStatus());
        verify(bookingRepository).save(booking);
    }

    @Test
    void expireDueBookings_shouldSkipTerminalBookings() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 24, 8, 0);

        Booking booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setShowtimeId(UUID.randomUUID());
        booking.setBookingStatus(BookingStatus.CONFIRMED);
        booking.setIsDeleted(false);
        booking.setReservedUntil(now.minusMinutes(1));
        booking.setSeatItems(List.of());

        when(bookingRepository.findDueBookingsForExpiration(eq(now), anyCollection()))
                .thenReturn(List.of(booking));

        List<BookingExpirationService.ExpiredBookingRelease> releases = bookingExpirationService.expireDueBookings(now);

        assertTrue(releases.isEmpty());
        assertEquals(BookingStatus.CONFIRMED, booking.getBookingStatus());
        verify(bookingRepository, never()).save(booking);
    }
}

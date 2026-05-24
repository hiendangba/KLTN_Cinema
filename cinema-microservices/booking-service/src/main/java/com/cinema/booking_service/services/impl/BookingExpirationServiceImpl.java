package com.cinema.booking_service.services.impl;

import com.cinema.booking_service.entity.Booking;
import com.cinema.booking_service.entity.BookingSeatItem;
import com.cinema.booking_service.enums.BookingStatus;
import com.cinema.booking_service.repository.BookingRepository;
import com.cinema.booking_service.services.BookingExpirationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingExpirationServiceImpl implements BookingExpirationService {

    private final BookingRepository bookingRepository;

    @Override
    @Transactional
    public List<ExpiredBookingRelease> expireDueBookings(LocalDateTime now) {
        List<Booking> dueBookings = bookingRepository.findDueBookingsForExpiration(
                now,
                EnumSet.of(BookingStatus.PENDING, BookingStatus.RESERVED));

        List<ExpiredBookingRelease> releases = new ArrayList<>();
        for (Booking booking : dueBookings) {
            if (!shouldExpire(booking, now)) {
                continue;
            }

            booking.setBookingStatus(BookingStatus.EXPIRED);
            bookingRepository.save(booking);
            releases.add(new ExpiredBookingRelease(
                    booking.getShowtimeId(),
                    extractSeatCodes(booking.getSeatItems())));
        }

        return releases;
    }

    private boolean shouldExpire(Booking booking, LocalDateTime now) {
        if (booking == null || Boolean.TRUE.equals(booking.getIsDeleted())) {
            return false;
        }
        if (booking.getBookingStatus() != BookingStatus.PENDING
                && booking.getBookingStatus() != BookingStatus.RESERVED) {
            return false;
        }
        return booking.getReservedUntil() != null && !booking.getReservedUntil().isAfter(now);
    }

    private List<String> extractSeatCodes(List<BookingSeatItem> seatItems) {
        if (seatItems == null || seatItems.isEmpty()) {
            return List.of();
        }

        return seatItems.stream()
                .map(BookingSeatItem::getSeatCode)
                .map(this::normalizeSeatCode)
                .toList();
    }

    private String normalizeSeatCode(String seatCode) {
        return seatCode == null ? "" : seatCode.trim().toUpperCase(Locale.ROOT);
    }
}

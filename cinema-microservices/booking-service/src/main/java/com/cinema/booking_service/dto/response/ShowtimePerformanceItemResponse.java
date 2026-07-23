package com.cinema.booking_service.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record ShowtimePerformanceItemResponse(
        UUID showtimeId,
        UUID cinemaId,
        String cinemaName,
        UUID filmId,
        String filmName,
        UUID hallId,
        String hallName,
        LocalDateTime startDateTime,
        LocalDateTime endDateTime,
        long totalBookings,
        long totalSeatsBooked,
        long totalSeatCapacity,
        BigDecimal occupancyRate) {
}

package com.cinema.booking_service.dto.response;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record ShowtimePerformanceSummaryResponse(
        long totalShowtimes,
        long totalBookings,
        long totalSeatsBooked,
        long totalSeatCapacity,
        BigDecimal occupancyRate) {
}

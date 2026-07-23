package com.cinema.booking_service.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record BookingRevenueItemResponse(
        UUID cinemaId,
        String cinemaName,
        long totalBookings,
        long pendingCount,
        long reservedCount,
        long confirmedCount,
        long expiredCount,
        BigDecimal conversionRate) {
}

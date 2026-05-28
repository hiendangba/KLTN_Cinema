package com.cinema.booking_service.dto.response;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record BookingRevenueSummaryResponse(
        long totalBookings,
        long pendingCount,
        long reservedCount,
        long confirmedCount,
        BigDecimal ticketSubtotalAmount,
        BigDecimal productSubtotalAmount,
        BigDecimal grossAmount) {
}

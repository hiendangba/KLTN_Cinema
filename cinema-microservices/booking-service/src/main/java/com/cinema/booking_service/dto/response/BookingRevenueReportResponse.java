package com.cinema.booking_service.dto.response;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record BookingRevenueReportResponse(
        LocalDateTime from,
        LocalDateTime to,
        LocalDateTime generatedAt,
        long currentPage,
        long totalPages,
        long totalElements,
        long size,
        boolean hasNext,
        boolean hasPrevious,
        BookingRevenueSummaryResponse page,
        BookingRevenueSummaryResponse total,
        List<BookingRevenueItemResponse> items) {
}

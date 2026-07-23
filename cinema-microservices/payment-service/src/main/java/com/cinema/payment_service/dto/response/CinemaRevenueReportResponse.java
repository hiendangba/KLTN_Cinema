package com.cinema.payment_service.dto.response;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record CinemaRevenueReportResponse(
        LocalDateTime from,
        LocalDateTime to,
        LocalDateTime generatedAt,
        long currentPage,
        long totalPages,
        long totalElements,
        long size,
        boolean hasNext,
        boolean hasPrevious,
        CinemaRevenueSummaryResponse page,
        CinemaRevenueSummaryResponse total,
        List<CinemaRevenueItemResponse> items) {
}

package com.cinema.payment_service.dto.response;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record FilmRevenueReportResponse(
        LocalDateTime from,
        LocalDateTime to,
        LocalDateTime generatedAt,
        long currentPage,
        long totalPages,
        long totalElements,
        long size,
        boolean hasNext,
        boolean hasPrevious,
        FilmRevenueSummaryResponse page,
        FilmRevenueSummaryResponse total,
        List<FilmRevenueItemResponse> items) {
}

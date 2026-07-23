package com.cinema.booking_service.dto.response;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record ShowtimePerformanceReportResponse(
        LocalDateTime from,
        LocalDateTime to,
        LocalDateTime generatedAt,
        long currentPage,
        long totalPages,
        long totalElements,
        long size,
        boolean hasNext,
        boolean hasPrevious,
        ShowtimePerformanceSummaryResponse page,
        ShowtimePerformanceSummaryResponse total,
        List<ShowtimePerformanceItemResponse> items) {
}

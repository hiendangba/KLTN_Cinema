package com.cinema.payment_service.dto.response;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record FilmRevenueSummaryResponse(
        long cinemaCount,
        long totalTransactions,
        long paidCount,
        long refundedCount,
        BigDecimal paidAmount) {
}

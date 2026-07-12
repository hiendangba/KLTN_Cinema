package com.cinema.payment_service.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record FilmRevenueItemResponse(
        UUID filmId,
        String filmName,
        String director,
        long cinemaCount,
        long totalTransactions,
        long paidCount,
        long refundedCount,
        BigDecimal paidAmount) {
}

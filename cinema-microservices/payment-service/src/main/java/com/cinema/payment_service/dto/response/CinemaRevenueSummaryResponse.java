package com.cinema.payment_service.dto.response;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record CinemaRevenueSummaryResponse(
        long totalTransactions,
        long pendingCount,
        long paidCount,
        long failedCount,
        long expiredCount,
        long refundPendingCount,
        long refundedCount,
        BigDecimal ticketSubtotalAmount,
        BigDecimal productSubtotalAmount,
        String promotionCode,
        String promotionName,
        BigDecimal promotionDiscountAmount,
        BigDecimal loyaltyPointsDiscountAmount,
        BigDecimal paidAmount,
        BigDecimal refundedAmount,
        BigDecimal grossAmount,
        BigDecimal netAmount) {
}

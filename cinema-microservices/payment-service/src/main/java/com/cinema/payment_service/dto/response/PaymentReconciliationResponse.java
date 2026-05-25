package com.cinema.payment_service.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
public record PaymentReconciliationResponse(
        LocalDateTime from,
        LocalDateTime to,
        long totalTransactions,
        long pendingCount,
        long paidCount,
        long failedCount,
        long expiredCount,
        long refundPendingCount,
        long refundedCount,
        BigDecimal paidAmount,
        BigDecimal refundedAmount,
        BigDecimal netAmount) {
}

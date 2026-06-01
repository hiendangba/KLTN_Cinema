package com.cinema.payment_service.dto.response;

import com.cinema.payment_service.enums.PaymentTransactionStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record PaymentReconciliationItemResponse(
        UUID id,
        UUID bookingId,
        UUID cinemaId,
        String cinemaName,
        UUID showtimeId,
        UUID userId,
        String orderInvoiceNumber,
        String providerRef,
        PaymentTransactionStatus status,
        BigDecimal amount,
        BigDecimal ticketSubtotalSnapshot,
        BigDecimal productSubtotalSnapshot,
        BigDecimal refundAmount,
        String paymentMethod,
        LocalDateTime expiresAt,
        LocalDateTime paidAt,
        LocalDateTime expiredAt,
        LocalDateTime refundedAt,
        LocalDateTime timeCreated,
        String failureReason,
        String promotionCode,
        String promotionName,
        BigDecimal promotionDiscountAmount) {
}

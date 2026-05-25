package com.cinema.payment_service.dto.response;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record PromotionPreviewResponse(
        String promotionCode,
        BigDecimal originalAmount,
        BigDecimal discountAmount,
        BigDecimal finalAmount,
        String note) {
}

package com.cinema.payment_service.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record PromotionSelectionItemResponse(
        UUID promotionId,
        String promotionCode,
        String promotionName,
        BigDecimal discountAmount,
        BigDecimal finalAmount,
        boolean applicable,
        String note) {
}

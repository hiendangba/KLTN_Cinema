package com.cinema.payment_service.support;

import java.math.BigDecimal;
import java.util.UUID;

public record PromotionQuote(
        String promotionCode,
        String promotionName,
        BigDecimal discountAmount,
        String note,
        UUID promotionId) {
}

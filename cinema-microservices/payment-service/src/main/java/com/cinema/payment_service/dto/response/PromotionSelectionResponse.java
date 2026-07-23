package com.cinema.payment_service.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Builder
public record PromotionSelectionResponse(
        UUID bookingId,
        BigDecimal originalAmount,
        List<PromotionSelectionItemResponse> promotions) {
}

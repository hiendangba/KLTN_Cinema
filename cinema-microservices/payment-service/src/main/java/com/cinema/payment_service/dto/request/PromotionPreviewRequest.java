package com.cinema.payment_service.dto.request;

import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class PromotionPreviewRequest {
    private UUID bookingId;
    private BigDecimal orderAmount;
    private UUID promotionId;
    private String promotionCode;
}

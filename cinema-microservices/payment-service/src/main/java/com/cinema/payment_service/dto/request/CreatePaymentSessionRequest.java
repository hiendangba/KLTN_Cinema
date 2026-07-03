package com.cinema.payment_service.dto.request;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.util.UUID;

@Data
public class CreatePaymentSessionRequest {
    private UUID bookingId;

    private UUID promotionId;

    @PositiveOrZero(message = "Loyalty points used must not be negative")
    private Long loyaltyPointsUsed;

    @Size(max = 80, message = "Promotion code must not exceed 80 characters")
    private String promotionCode;
}

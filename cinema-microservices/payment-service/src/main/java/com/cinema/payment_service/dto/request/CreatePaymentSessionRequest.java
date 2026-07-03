package com.cinema.payment_service.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class CreatePaymentSessionRequest {
    private UUID bookingId;

    private UUID promotionId;

    @Size(max = 80, message = "Promotion code must not exceed 80 characters")
    private String promotionCode;
}

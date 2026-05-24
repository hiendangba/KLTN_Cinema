package com.cinema.payment_service.dto.request;

import lombok.Data;

import java.util.UUID;

@Data
public class CreatePaymentSessionRequest {
    private UUID bookingId;
}

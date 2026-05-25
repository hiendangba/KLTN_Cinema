package com.cinema.payment_service.dto.request;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RefundPaymentRequest {
    private BigDecimal refundAmount;
    private String reason;
}

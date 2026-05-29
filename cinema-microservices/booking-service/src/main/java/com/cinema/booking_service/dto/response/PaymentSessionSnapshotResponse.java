package com.cinema.booking_service.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class PaymentSessionSnapshotResponse {
    private UUID id;
    private UUID bookingId;
    private UUID showtimeId;
    private UUID userId;
    private String paymentMethod;
    private String orderInvoiceNumber;
    private String providerRef;
    private String payUrl;
    private String status;
    private BigDecimal amount;
    private String promotionCode;
    private String promotionName;
    private BigDecimal promotionDiscountAmount;
    private LocalDateTime expiresAt;
    private LocalDateTime paidAt;
    private LocalDateTime expiredAt;
    private String failureReason;
}

package com.cinema.booking_service.dto.response;

import lombok.Data;

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
    private String checkoutUrl;
    private String status;
    private LocalDateTime expiresAt;
    private LocalDateTime paidAt;
    private LocalDateTime expiredAt;
    private String failureReason;
}

package com.cinema.payment_service.dto.response;

import com.cinema.payment_service.enums.PaymentTransactionStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class PaymentSessionResponse {
    private UUID id;
    private UUID bookingId;
    private UUID showtimeId;
    private UUID cinemaId;
    private UUID userId;
    private BigDecimal amount;
    private BigDecimal ticketSubtotalSnapshot;
    private BigDecimal productSubtotalSnapshot;
    private String currency;
    private String paymentMethod;
    private String orderInvoiceNumber;
    private String providerRef;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private UUID completedByUserId;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String completedByRole;
    private String payUrl;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String qrCodeUrl;
    private Map<String, String> checkoutFields;
    private PaymentTransactionStatus status;
    private LocalDateTime expiresAt;
    private LocalDateTime paidAt;
    private LocalDateTime expiredAt;
    private String failureReason;
    private BigDecimal refundAmount;
    private String refundReason;
    private LocalDateTime refundedAt;
    private String promotionCode;
    private String promotionName;
    private BigDecimal promotionDiscountAmount;
}

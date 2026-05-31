package com.cinema.payment_service.entity;

import com.cinema.payment_service.enums.PaymentTransactionStatus;
import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment_transaction")
@Getter
@Setter
public class PaymentTransaction {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "booking_id", columnDefinition = "uuid", nullable = false)
    private UUID bookingId;

    @Column(name = "showtime_id", columnDefinition = "uuid", nullable = false)
    private UUID showtimeId;

    @Column(name = "cinema_id", columnDefinition = "uuid")
    private UUID cinemaId;

    @Column(name = "film_id", columnDefinition = "uuid")
    private UUID filmId;

    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "ticket_subtotal_snapshot", precision = 12, scale = 2)
    private BigDecimal ticketSubtotalSnapshot;

    @Column(name = "product_subtotal_snapshot", precision = 12, scale = 2)
    private BigDecimal productSubtotalSnapshot;

    @Column(name = "currency", nullable = false, length = 8)
    private String currency;

    @Column(name = "payment_method", nullable = false, length = 40)
    private String paymentMethod;

    @Column(name = "order_invoice_number", nullable = false, unique = true, length = 120)
    private String orderInvoiceNumber;

    @Column(name = "provider_ref", length = 120)
    private String providerRef;

    @Column(name = "checkout_url", nullable = false, length = 255)
    private String payUrl;

    @Column(name = "checkout_payload_json", columnDefinition = "text")
    private String checkoutPayloadJson;

    @Column(name = "response_payload_json", columnDefinition = "text")
    private String responsePayloadJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentTransactionStatus status;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "expired_at")
    private LocalDateTime expiredAt;

    @Column(name = "failure_reason", columnDefinition = "text")
    private String failureReason;

    @Column(name = "refund_amount", precision = 12, scale = 2)
    private BigDecimal refundAmount;

    @Column(name = "refund_reason", columnDefinition = "text")
    private String refundReason;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    @Column(name = "promotion_code", length = 80)
    private String promotionCode;

    @Column(name = "promotion_name", length = 120)
    private String promotionName;

    @Column(name = "promotion_discount_amount", precision = 12, scale = 2)
    private BigDecimal promotionDiscountAmount;

    @Column(name = "webhook_event_key", length = 255)
    private String webhookEventKey;

    @Column(name = "last_webhook_at")
    private LocalDateTime lastWebhookAt;

    @Column(name = "time_created", nullable = false, updatable = false)
    private LocalDateTime timeCreated;

    @Column(name = "time_updated", nullable = false)
    private LocalDateTime timeUpdated;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UuidCreator.getTimeOrderedEpoch();
        }
        if (status == null) {
            status = PaymentTransactionStatus.PENDING;
        }
        LocalDateTime now = LocalDateTime.now();
        timeCreated = now;
        timeUpdated = now;
    }

    @PreUpdate
    public void preUpdate() {
        timeUpdated = LocalDateTime.now();
    }
}

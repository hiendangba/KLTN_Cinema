package com.cinema.payment_service.services;

import com.cinema.payment_service.dto.request.CreatePaymentSessionRequest;
import com.cinema.payment_service.dto.request.PromotionPreviewRequest;
import com.cinema.payment_service.dto.request.RefundPaymentRequest;
import com.cinema.payment_service.dto.response.PaymentReconciliationResponse;
import com.cinema.payment_service.dto.response.PromotionPreviewResponse;
import com.cinema.payment_service.dto.response.PaymentSessionResponse;
import com.cinema.payment_service.dto.webhook.SePayIpnRequest;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public interface PaymentSessionService {
    PaymentSessionResponse createSession(CreatePaymentSessionRequest request, UUID requesterUserId);

    PaymentSessionResponse getSession(UUID bookingId, UUID requesterUserId);

    PaymentSessionResponse requestRefund(UUID bookingId, UUID requesterUserId, RefundPaymentRequest request);

    PaymentReconciliationResponse getReconciliation(LocalDateTime from, LocalDateTime to);

    PromotionPreviewResponse previewPromotion(PromotionPreviewRequest request, UUID requesterUserId);

    WebhookProcessingResult handleSePayWebhook(String secretKey, SePayIpnRequest request);

    void expireDueSessions();

    record WebhookProcessingResult(HttpStatus status, Map<String, Object> body) {
    }
}

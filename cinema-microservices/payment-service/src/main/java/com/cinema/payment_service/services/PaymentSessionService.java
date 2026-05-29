package com.cinema.payment_service.services;

import com.cinema.payment_service.dto.request.CreatePaymentSessionRequest;
import com.cinema.payment_service.dto.request.CinemaRevenueReportRequest;
import com.cinema.payment_service.dto.request.PaymentSessionField;
import com.cinema.payment_service.dto.request.PromotionPreviewRequest;
import com.cinema.payment_service.dto.request.RefundPaymentRequest;
import com.cinema.payment_service.dto.response.CinemaRevenueReportResponse;
import com.cinema.payment_service.dto.response.PaymentReconciliationResponse;
import com.cinema.payment_service.dto.response.PromotionPreviewResponse;
import com.cinema.payment_service.dto.response.PaymentSessionResponse;
import com.cinema.payment_service.dto.webhook.SePayIpnRequest;
import com.cinema.dto.request.PageRequest;
import com.cinema.dto.response.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public interface PaymentSessionService {
    PaymentSessionResponse createSession(CreatePaymentSessionRequest request, UUID requesterUserId);

    PaymentSessionResponse getSession(UUID bookingId, UUID requesterUserId);

    PageResponse<PaymentSessionResponse> searchMySessions(
            PageRequest<PaymentSessionField> request,
            UUID requesterUserId);

    PaymentSessionResponse requestRefund(UUID bookingId, UUID requesterUserId, RefundPaymentRequest request);

    PaymentReconciliationResponse getReconciliation(LocalDateTime from, LocalDateTime to);

    CinemaRevenueReportResponse getAllCinemaRevenueReport(CinemaRevenueReportRequest request);

    CinemaRevenueReportResponse getMyCinemaRevenueReport(CinemaRevenueReportRequest request, UUID requesterUserId);

    byte[] exportCinemaRevenueReport(CinemaRevenueReportRequest request, HttpServletRequest httpRequest);

    PromotionPreviewResponse previewPromotion(PromotionPreviewRequest request, UUID requesterUserId);

    WebhookProcessingResult handleSePayWebhook(String secretKey, SePayIpnRequest request);

    void expireDueSessions();

    record WebhookProcessingResult(HttpStatus status, Map<String, Object> body) {
    }
}

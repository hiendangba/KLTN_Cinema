package com.cinema.payment_service.controller;

import com.cinema.payment_service.dto.request.CreatePaymentSessionRequest;
import com.cinema.payment_service.dto.request.PromotionPreviewRequest;
import com.cinema.payment_service.dto.request.RefundPaymentRequest;
import com.cinema.payment_service.dto.response.PaymentReconciliationResponse;
import com.cinema.payment_service.dto.response.PromotionPreviewResponse;
import com.cinema.payment_service.dto.response.PaymentSessionResponse;
import com.cinema.payment_service.dto.response.VietQrBankResponse;
import com.cinema.payment_service.dto.webhook.SePayIpnRequest;
import com.cinema.payment_service.services.PaymentSessionService;
import com.cinema.payment_service.services.VietQrService;
import com.cinema.controller.BaseController;
import com.cinema.dto.response.APIResponse;
import com.cinema.http.RequestAuthUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController extends BaseController {

    private final VietQrService vietQrService;
    private final PaymentSessionService paymentSessionService;

    @GetMapping("/vietqr/banks")
    public ResponseEntity<APIResponse<java.util.List<VietQrBankResponse>>> getVietQrBanks() {
        return ok(vietQrService.getBanks());
    }

    @PostMapping("/sessions")
    public ResponseEntity<APIResponse<PaymentSessionResponse>> createSession(
            HttpServletRequest servletRequest,
            @RequestBody CreatePaymentSessionRequest request) {
        UUID requesterUserId = RequestAuthUtils.requireUserId(servletRequest);
        return ok(paymentSessionService.createSession(request, requesterUserId));
    }

    @GetMapping("/sessions/{bookingId}")
    public ResponseEntity<APIResponse<PaymentSessionResponse>> getSession(
            HttpServletRequest servletRequest,
            @PathVariable UUID bookingId) {
        UUID requesterUserId = RequestAuthUtils.requireUserId(servletRequest);
        return ok(paymentSessionService.getSession(bookingId, requesterUserId));
    }

    @PostMapping("/sessions/{bookingId}/refund")
    public ResponseEntity<APIResponse<PaymentSessionResponse>> requestRefund(
            HttpServletRequest servletRequest,
            @PathVariable UUID bookingId,
            @RequestBody(required = false) RefundPaymentRequest request) {
        UUID requesterUserId = RequestAuthUtils.requireUserId(servletRequest);
        return ok(paymentSessionService.requestRefund(bookingId, requesterUserId, request));
    }

    @GetMapping("/reconciliation")
    public ResponseEntity<APIResponse<PaymentReconciliationResponse>> getReconciliation(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ok(paymentSessionService.getReconciliation(from, to));
    }

    @PostMapping("/promotions/preview")
    public ResponseEntity<APIResponse<PromotionPreviewResponse>> previewPromotion(
            HttpServletRequest servletRequest,
            @RequestBody PromotionPreviewRequest request) {
        UUID requesterUserId = RequestAuthUtils.requireUserId(servletRequest);
        return ok(paymentSessionService.previewPromotion(request, requesterUserId));
    }

    @PostMapping("/webhooks/sepay")
    public ResponseEntity<Map<String, Object>> handleSePayWebhook(
            @RequestHeader(value = "X-Secret-Key", required = false) String secretKey,
            @RequestBody(required = false) SePayIpnRequest request) {
        PaymentSessionService.WebhookProcessingResult result = paymentSessionService.handleSePayWebhook(secretKey, request);
        return ResponseEntity.status(result.status()).body(result.body());
    }
}

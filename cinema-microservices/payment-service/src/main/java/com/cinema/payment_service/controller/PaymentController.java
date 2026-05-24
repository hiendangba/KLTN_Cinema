package com.cinema.payment_service.controller;

import com.cinema.payment_service.dto.request.CreatePaymentSessionRequest;
import com.cinema.payment_service.dto.response.PaymentSessionResponse;
import com.cinema.payment_service.dto.response.VietQrBankResponse;
import com.cinema.payment_service.dto.webhook.SePayIpnRequest;
import com.cinema.payment_service.services.PaymentSessionService;
import com.cinema.payment_service.services.VietQrService;
import com.cinema.controller.BaseController;
import com.cinema.dto.response.APIResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

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
    public ResponseEntity<APIResponse<PaymentSessionResponse>> createSession(@RequestBody CreatePaymentSessionRequest request) {
        return ok(paymentSessionService.createSession(request));
    }

    @GetMapping("/sessions/{bookingId}")
    public ResponseEntity<APIResponse<PaymentSessionResponse>> getSession(@PathVariable UUID bookingId) {
        return ok(paymentSessionService.getSession(bookingId));
    }

    @PostMapping("/webhooks/sepay")
    public ResponseEntity<Map<String, Object>> handleSePayWebhook(
            @RequestHeader(value = "X-Secret-Key", required = false) String secretKey,
            @RequestBody(required = false) SePayIpnRequest request) {
        PaymentSessionService.WebhookProcessingResult result = paymentSessionService.handleSePayWebhook(secretKey, request);
        return ResponseEntity.status(result.status()).body(result.body());
    }
}

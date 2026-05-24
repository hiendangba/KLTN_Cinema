package com.cinema.payment_service.services;

import com.cinema.payment_service.dto.request.CreatePaymentSessionRequest;
import com.cinema.payment_service.dto.response.PaymentSessionResponse;
import com.cinema.payment_service.dto.webhook.SePayIpnRequest;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public interface PaymentSessionService {
    PaymentSessionResponse createSession(CreatePaymentSessionRequest request);

    PaymentSessionResponse getSession(UUID bookingId);

    WebhookProcessingResult handleSePayWebhook(String secretKey, SePayIpnRequest request);

    void expireDueSessions();

    record WebhookProcessingResult(HttpStatus status, Map<String, Object> body) {
    }
}

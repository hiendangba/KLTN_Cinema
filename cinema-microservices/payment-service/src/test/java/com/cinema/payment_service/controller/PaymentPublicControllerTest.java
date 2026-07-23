package com.cinema.payment_service.controller;

import com.cinema.payment_service.dto.momo.MomoIpnRequest;
import com.cinema.payment_service.services.PaymentSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;

import java.util.HashMap;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentPublicControllerTest {

    private PaymentSessionService paymentSessionService;
    private PaymentPublicController controller;

    @BeforeEach
    void setUp() {
        paymentSessionService = Mockito.mock(PaymentSessionService.class);
        controller = new PaymentPublicController(paymentSessionService);
    }

    @Test
    void paymentResult_shouldReturnHtmlPage() {
        when(paymentSessionService.handleMomoReturn(any(MomoIpnRequest.class)))
                .thenReturn(new PaymentSessionService.WebhookProcessingResult(
                        HttpStatus.NO_CONTENT,
                        Map.of("success", true)));

        String body = controller.paymentResult(Map.of(
                        "orderId", "PAY-1",
                        "resultCode", "0"))
                .getBody();

        verify(paymentSessionService).handleMomoReturn(any(MomoIpnRequest.class));
        assertTrue(body.contains("Payment completed."));
        assertTrue(body.contains("You can close this page"));
    }

    @Test
    void paymentResult_shouldIgnoreProcessingErrorAndStillReturnHtml() {
        when(paymentSessionService.handleMomoReturn(any(MomoIpnRequest.class)))
                .thenThrow(new RuntimeException("unexpected"));

        Map<String, String> query = new HashMap<>();
        query.put("orderId", "PAY-2");
        query.put("resultCode", "0");

        String body = controller.paymentResult(query).getBody();

        verify(paymentSessionService).handleMomoReturn(any(MomoIpnRequest.class));
        assertTrue(body.contains("Payment completed."));
    }
}

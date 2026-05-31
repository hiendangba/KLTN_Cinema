package com.cinema.payment_service.controller;

import com.cinema.payment_service.services.PaymentSessionService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentPublicControllerTest {

    @Test
    void paymentResult_shouldReturnHtmlPage() {
        PaymentSessionService paymentSessionService = Mockito.mock(PaymentSessionService.class);
        PaymentPublicController controller = new PaymentPublicController(paymentSessionService);

        String body = controller.paymentResult(Map.of(
                        "orderId", "PAY-1",
                        "resultCode", "0"))
                .getBody();

        assertTrue(body.contains("Payment completed."));
        assertTrue(body.contains("You can close this page"));
    }
}

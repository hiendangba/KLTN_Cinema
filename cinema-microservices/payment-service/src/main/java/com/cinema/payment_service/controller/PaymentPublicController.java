package com.cinema.payment_service.controller;

import com.cinema.payment_service.dto.momo.MomoIpnRequest;
import com.cinema.payment_service.services.PaymentSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
public class PaymentPublicController {

    private final PaymentSessionService paymentSessionService;

    @GetMapping(value = "/payment/result", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> paymentResult(@RequestParam Map<String, String> queryParams) {
        String orderId = queryParams.getOrDefault("orderId", "");
        String requestId = queryParams.getOrDefault("requestId", "");
        String resultCode = queryParams.getOrDefault("resultCode", "");
        String transId = queryParams.getOrDefault("transId", "");

        log.info(
                "MOMO_RETURN_RECEIVED orderId={} requestId={} resultCode={} transId={}",
                orderId,
                requestId,
                resultCode,
                transId);

        boolean success = "0".equals(resultCode) || "9000".equals(resultCode);
        String message = success ? "Payment completed." : "Payment not completed.";
        String html = """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>Payment result</title>
                  <style>
                    body { font-family: Arial, sans-serif; margin: 0; padding: 32px; background: #f6f8fb; color: #111827; }
                    .card { max-width: 560px; margin: 0 auto; background: #fff; border-radius: 16px; padding: 24px; box-shadow: 0 12px 30px rgba(0,0,0,.08); }
                    h1 { margin: 0 0 12px; font-size: 24px; }
                    p { margin: 0; line-height: 1.6; }
                  </style>
                </head>
                <body>
                  <div class="card">
                    <h1>%s</h1>
                    <p>You can close this page and return to the app.</p>
                  </div>
                </body>
                </html>
                """.formatted(message);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }

    @PostMapping("/ipn")
    public ResponseEntity<?> handleMomoIpnAlias(@RequestBody(required = false) MomoIpnRequest request) {
        PaymentSessionService.WebhookProcessingResult result = paymentSessionService.handleMomoWebhook(request);
        log.info(
                "MOMO_IPN_ALIAS_HTTP_RESPONSE orderId={} requestId={} resultCode={} status={}",
                request == null || request.orderId() == null ? "" : request.orderId(),
                request == null || request.requestId() == null ? "" : request.requestId(),
                request == null || request.resultCode() == null ? "" : request.resultCode(),
                result.status().value());
        if (result.status().is2xxSuccessful() && result.status().value() == 204) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.status(result.status()).body(result.body());
    }
}

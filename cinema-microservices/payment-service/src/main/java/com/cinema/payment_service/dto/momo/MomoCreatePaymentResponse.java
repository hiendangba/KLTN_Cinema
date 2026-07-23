package com.cinema.payment_service.dto.momo;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MomoCreatePaymentResponse(
        @JsonProperty("partnerCode") String partnerCode,
        @JsonProperty("requestId") String requestId,
        @JsonProperty("orderId") String orderId,
        @JsonProperty("amount") Long amount,
        @JsonProperty("responseTime") Long responseTime,
        @JsonProperty("message") String message,
        @JsonProperty("resultCode") Integer resultCode,
        @JsonProperty("payUrl") String payUrl,
        @JsonProperty("qrCodeUrl") String qrCodeUrl,
        @JsonProperty("deeplink") String deeplink,
        @JsonProperty("deeplinkMiniApp") String deeplinkMiniApp
) {
}

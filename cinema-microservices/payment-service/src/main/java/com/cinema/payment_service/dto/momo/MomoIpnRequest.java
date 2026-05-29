package com.cinema.payment_service.dto.momo;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MomoIpnRequest(
        @JsonProperty("partnerCode") String partnerCode,
        @JsonProperty("orderId") String orderId,
        @JsonProperty("requestId") String requestId,
        @JsonProperty("amount") Long amount,
        @JsonProperty("orderInfo") String orderInfo,
        @JsonProperty("orderType") String orderType,
        @JsonProperty("transId") Long transId,
        @JsonProperty("resultCode") Integer resultCode,
        @JsonProperty("message") String message,
        @JsonProperty("payType") String payType,
        @JsonProperty("responseTime") Long responseTime,
        @JsonProperty("extraData") String extraData,
        @JsonProperty("signature") String signature
) {
}

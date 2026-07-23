package com.cinema.payment_service.dto.momo;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.util.StringUtils;

import java.util.Map;

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
    public static MomoIpnRequest fromQueryParams(Map<String, String> queryParams) {
        return new MomoIpnRequest(
                queryParams.get("partnerCode"),
                queryParams.get("orderId"),
                queryParams.get("requestId"),
                parseLong(queryParams.get("amount")),
                queryParams.get("orderInfo"),
                queryParams.get("orderType"),
                parseLong(queryParams.get("transId")),
                parseInteger(queryParams.get("resultCode")),
                queryParams.get("message"),
                queryParams.get("payType"),
                parseLong(queryParams.get("responseTime")),
                queryParams.get("extraData"),
                queryParams.get("signature"));
    }

    private static Long parseLong(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        try {
            return Long.parseLong(rawValue.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Integer parseInteger(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        try {
            return Integer.parseInt(rawValue.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}

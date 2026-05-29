package com.cinema.payment_service.support;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.payment_service.config.MomoGatewayProperties;
import com.cinema.payment_service.dto.momo.MomoCreatePaymentResponse;
import com.cinema.payment_service.entity.PaymentTransaction;
import com.cinema.payment_service.grpc.BookingGrpcClient.BookingPaymentContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MomoPaymentGatewayClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final MomoGatewayProperties properties;
    private final ObjectMapper objectMapper;
    private HttpClient httpClient;

    @PostConstruct
    void init() {
        httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
    }

    public MomoCheckoutResult createCheckout(PaymentTransaction transaction, BookingPaymentContext bookingContext) {
        if (transaction == null || bookingContext == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        Map<String, Object> requestFields = buildRequestFields(transaction, bookingContext);
        String signature = sign(requestFields);
        requestFields.put("signature", signature);

        String requestJson = writeJson(requestFields);
        String responseJson = invokeCreateApi(requestJson);
        MomoCreatePaymentResponse response = readJson(responseJson);

        if (response == null || response.resultCode() == null || response.resultCode() != 0
                || !StringUtils.hasText(response.payUrl())) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }

        return new MomoCheckoutResult(response.payUrl(), response.qrCodeUrl(), requestJson, responseJson);
    }

    private Map<String, Object> buildRequestFields(PaymentTransaction transaction, BookingPaymentContext bookingContext) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("partnerCode", properties.getPartnerCode());
        fields.put("requestId", transaction.getOrderInvoiceNumber());
        fields.put("amount", normalizeAmount(transaction.getAmount()));
        fields.put("orderId", transaction.getOrderInvoiceNumber());
        fields.put("orderInfo", buildOrderInfo(transaction, bookingContext));
        fields.put("redirectUrl", properties.getRedirectUrl());
        fields.put("ipnUrl", properties.getIpnUrl());
        fields.put("extraData", "");
        fields.put("requestType", properties.getRequestType());
        fields.put("lang", properties.getLang());
        return fields;
    }

    private String buildOrderInfo(PaymentTransaction transaction, BookingPaymentContext bookingContext) {
        return "CinemaStar booking " + transaction.getBookingId() + " showtime " + bookingContext.showtimeId();
    }

    private Long normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            return 0L;
        }
        return amount.setScale(0, java.math.RoundingMode.HALF_UP).longValue();
    }

    private String sign(Map<String, Object> fields) {
        StringBuilder signed = new StringBuilder();
        appendSignedField(signed, "accessKey", properties.getAccessKey());
        appendSignedField(signed, "amount", asString(fields.get("amount")));
        appendSignedField(signed, "extraData", asString(fields.get("extraData")));
        appendSignedField(signed, "ipnUrl", asString(fields.get("ipnUrl")));
        appendSignedField(signed, "orderId", asString(fields.get("orderId")));
        appendSignedField(signed, "orderInfo", asString(fields.get("orderInfo")));
        appendSignedField(signed, "partnerCode", asString(fields.get("partnerCode")));
        appendSignedField(signed, "redirectUrl", asString(fields.get("redirectUrl")));
        appendSignedField(signed, "requestId", asString(fields.get("requestId")));
        appendSignedField(signed, "requestType", asString(fields.get("requestType")));

        return hmacSha256Hex(signed.toString(), properties.getSecretKey());
    }

    private void appendSignedField(StringBuilder signed, String key, String value) {
        if (signed.length() > 0) {
            signed.append('&');
        }
        signed.append(key).append('=').append(value == null ? "" : value);
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String hmacSha256Hex(String value, String secretKey) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private String invokeCreateApi(String requestJson) {
        String endpoint = properties.getBaseUrl().replaceAll("/+$", "") + "/v2/gateway/api/create";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR);
            }
            return response.body();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private MomoCreatePaymentResponse readJson(String json) {
        try {
            return objectMapper.readValue(json, MomoCreatePaymentResponse.class);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private String writeJson(Map<String, Object> fields) {
        try {
            return objectMapper.writeValueAsString(fields);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    public record MomoCheckoutResult(String payUrl,
                                     String qrCodeUrl,
                                     String requestPayloadJson,
                                     String responsePayloadJson) {
    }
}

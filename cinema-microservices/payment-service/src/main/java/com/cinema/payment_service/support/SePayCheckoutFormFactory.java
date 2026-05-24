package com.cinema.payment_service.support;

import com.cinema.payment_service.config.SePayGatewayProperties;
import com.cinema.payment_service.entity.PaymentTransaction;
import com.cinema.payment_service.grpc.BookingGrpcClient.BookingPaymentContext;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SePayCheckoutFormFactory {

    private static final List<String> SIGNED_FIELD_ORDER = List.of(
            "order_amount",
            "merchant",
            "currency",
            "operation",
            "order_description",
            "order_invoice_number",
            "customer_id",
            "payment_method",
            "success_url",
            "error_url",
            "cancel_url"
    );

    private SePayCheckoutFormFactory() {
    }

    public static CheckoutForm build(SePayGatewayProperties properties,
                                     PaymentTransaction transaction,
                                     BookingPaymentContext bookingContext) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("order_amount", normalizeAmount(transaction.getAmount()));
        fields.put("merchant", properties.getMerchantId());
        fields.put("currency", transaction.getCurrency());
        fields.put("operation", "PURCHASE");
        fields.put("order_description", buildDescription(transaction, bookingContext));
        fields.put("order_invoice_number", transaction.getOrderInvoiceNumber());
        fields.put("customer_id", bookingContext.userId().toString());
        fields.put("payment_method", transaction.getPaymentMethod());
        fields.put("success_url", appendStatus(properties.getReturnUrl(), "success"));
        fields.put("error_url", appendStatus(properties.getReturnUrl(), "error"));
        fields.put("cancel_url", appendStatus(properties.getReturnUrl(), "cancel"));

        String signature = sign(fields, properties.getSecretKey());
        fields.put("signature", signature);

        return new CheckoutForm(properties.getBaseUrl().replaceAll("/+$", "") + "/v1/checkout/init", fields);
    }

    private static String normalizeAmount(java.math.BigDecimal amount) {
        return amount == null ? "0" : amount.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    private static String buildDescription(PaymentTransaction transaction, BookingPaymentContext bookingContext) {
        return "CinemaStar booking " + transaction.getBookingId() + " showtime " + bookingContext.showtimeId();
    }

    private static String appendStatus(String baseUrl, String status) {
        return UriComponentsBuilder.fromUriString(baseUrl)
                .queryParam("status", status)
                .build(true)
                .toUriString();
    }

    private static String sign(Map<String, String> fields, String secretKey) {
        StringBuilder signed = new StringBuilder();
        for (String field : SIGNED_FIELD_ORDER) {
            String value = fields.get(field);
            if (value == null) {
                continue;
            }
            if (signed.length() > 0) {
                signed.append(',');
            }
            signed.append(field).append('=').append(value);
        }
        return java.util.Base64.getEncoder().encodeToString(
                hmacSha256(signed.toString(), secretKey));
    }

    private static byte[] hmacSha256(String value, String secretKey) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secretKey.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to sign SePay checkout form", ex);
        }
    }

    public record CheckoutForm(String checkoutUrl, Map<String, String> fields) {
    }
}

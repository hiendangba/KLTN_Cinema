package com.cinema.payment_service.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@Getter
@Setter
@ConfigurationProperties(prefix = "sepay")
public class SePayGatewayProperties {
    private String baseUrl;
    private String merchantId;
    private String secretKey;
    private String paymentMethod;
    private String returnUrl;
    private String ipnUrl;

    @PostConstruct
    void validate() {
        requireText(baseUrl, "SEPAY_BASE_URL");
        requireText(merchantId, "SEPAY_MERCHANT_ID");
        requireText(secretKey, "SEPAY_SECRET_KEY");
        requireText(paymentMethod, "SEPAY_PAYMENT_METHOD");
        requireText(returnUrl, "SEPAY_RETURN_URL");
        requireText(ipnUrl, "SEPAY_IPN_URL");
    }

    private void requireText(String value, String envName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("Missing " + envName + " environment variable");
        }
    }
}

package com.cinema.payment_service.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@Getter
@Setter
@ConfigurationProperties(prefix = "momo")
public class MomoGatewayProperties {
    private String baseUrl;
    private String partnerCode;
    private String accessKey;
    private String secretKey;
    private String redirectUrl;
    private String ipnUrl;
    private String requestType = "captureWallet";
    private String lang = "vi";

    @PostConstruct
    void validate() {
        requireText(baseUrl, "MOMO_BASE_URL");
        requireText(partnerCode, "MOMO_PARTNER_CODE");
        requireText(accessKey, "MOMO_ACCESS_KEY");
        requireText(secretKey, "MOMO_SECRET_KEY");
        requireText(redirectUrl, "MOMO_RETURN_URL");
        requireText(ipnUrl, "MOMO_IPN_URL");
    }

    private void requireText(String value, String envName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("Missing " + envName + " environment variable");
        }
    }
}

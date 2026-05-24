package com.cinema.payment_service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "vietqr")
public class VietQrProperties {
    private String baseUrl;
    private String banksPath;
    private String apiKey;
    private String secretKey;
}

package com.cinema.payment_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Configuration
public class VietQrClientConfig {

    @Bean
    RestClient vietQrRestClient(VietQrProperties vietQrProperties) {
        if (!StringUtils.hasText(vietQrProperties.getBaseUrl())) {
            throw new IllegalStateException("Missing VIETQR_BASE_URL environment variable");
        }
        return RestClient.builder()
                .baseUrl(vietQrProperties.getBaseUrl())
                .build();
    }
}

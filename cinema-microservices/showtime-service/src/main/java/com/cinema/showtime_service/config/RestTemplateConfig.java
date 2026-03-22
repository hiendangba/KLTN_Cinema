package com.cinema.showtime_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2 * 1000); // 2 giây
        factory.setReadTimeout(5 * 1000); //5 giây
        RestTemplate restTemplate = new RestTemplate(factory);
//        restTemplate.setErrorHandler(new CustomResponseErrorHandler());

        return restTemplate;
    }
}

package com.cinema.exception;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.UrlHandlerFilter;

@Configuration
public class ApiPathNormalizationConfig {

    @Bean
    public FilterRegistrationBean<UrlHandlerFilter> apiPathNormalizationFilter() {
        UrlHandlerFilter filter = UrlHandlerFilter
                .trailingSlashHandler("/api/**").wrapRequest()
                .trailingSlashHandler("/internal/auth/**").wrapRequest()
                .build();

        FilterRegistrationBean<UrlHandlerFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}

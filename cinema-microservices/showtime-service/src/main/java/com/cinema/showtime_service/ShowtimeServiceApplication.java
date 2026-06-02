package com.cinema.showtime_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.cinema.showtime_service.config.ShowTimeStatusSchedulerProperties;

@EnableCaching
@EnableScheduling
@EnableConfigurationProperties(ShowTimeStatusSchedulerProperties.class)
@SpringBootApplication(scanBasePackages = "com.cinema")
public class ShowtimeServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShowtimeServiceApplication.class, args);
    }

}

package com.cinema.showtime_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableCaching
@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.cinema")
public class ShowtimeServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShowtimeServiceApplication.class, args);
    }

}

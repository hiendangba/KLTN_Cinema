package com.cinema.hall_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.cinema")
public class HallServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(HallServiceApplication.class, args);
    }
}

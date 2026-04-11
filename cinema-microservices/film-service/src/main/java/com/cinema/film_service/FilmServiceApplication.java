package com.cinema.film_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@EnableCaching
@SpringBootApplication(scanBasePackages = "com.cinema")
public class FilmServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FilmServiceApplication.class, args);
    }
}   

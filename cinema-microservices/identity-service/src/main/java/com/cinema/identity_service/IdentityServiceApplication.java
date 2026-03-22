package com.cinema.identity_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

import org.springframework.scheduling.annotation.EnableAsync;

@ComponentScan(basePackages = {
		"com.cinema.identity_service",
		"com.cinema.exception"
})
@SpringBootApplication
@EnableAsync
public class IdentityServiceApplication {
	public static void main(String[] args) {
		SpringApplication.run(IdentityServiceApplication.class, args);
	}
}
